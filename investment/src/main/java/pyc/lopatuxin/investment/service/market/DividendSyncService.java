package pyc.lopatuxin.investment.service.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.client.tinvest.TinvestApi;
import pyc.lopatuxin.investment.client.tinvest.TinvestBondCouponsRequest;
import pyc.lopatuxin.investment.client.tinvest.TinvestBondCouponsResponse;
import pyc.lopatuxin.investment.client.tinvest.TinvestCoupon;
import pyc.lopatuxin.investment.client.tinvest.TinvestDividend;
import pyc.lopatuxin.investment.client.tinvest.TinvestDividendsRequest;
import pyc.lopatuxin.investment.client.tinvest.TinvestDividendsResponse;
import pyc.lopatuxin.investment.client.tinvest.TinvestResilience;
import pyc.lopatuxin.investment.client.tinvest.TinvestUnauthorizedException;
import pyc.lopatuxin.investment.config.TinvestProperties;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.DividendSource;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.PayoutKind;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DividendSyncService {

    // ProjectionService averages payouts over the last five full calendar years (its own
    // PAYOUT_WINDOW_YEARS) — the earliest date this sync must ever reach is January 1st of
    // (currentYear - 5), which is up to ~72 months back when "today" falls in December. 24
    // months only covered the last ~2 years, so a bond whose price history predates that window
    // (divisor stays 5, per plan point 11) had its coupon sum computed from ~1 year of coupons
    // instead of 5 — understating payout yield roughly threefold (see SU26219RMFS4 in QA-3).
    private static final LocalDate WINDOW_START_FLOOR = LocalDate.of(2016, 1, 1);
    private static final int WINDOW_BACK_MONTHS = 72;
    private static final int WINDOW_FORWARD_MONTHS = 18;
    private static final int STARTUP_STALE_HOURS = 20;

    // GetDividends only supports shares and ETFs; a BOND/OFZ pays coupons instead, fetched via
    // GetBondCoupons (see fetchCoupons/mergeCoupon below).
    private static final Set<SecurityType> DIVIDEND_ELIGIBLE_TYPES = EnumSet.of(SecurityType.STOCK, SecurityType.ETF);
    private static final Set<SecurityType> COUPON_ELIGIBLE_TYPES = EnumSet.of(SecurityType.BOND, SecurityType.OFZ);

    private final TinvestApi tinvestApi;
    private final TinvestResilience tinvestResilience;
    private final TinvestProperties tinvestProperties;
    private final TinvestInstrumentResolver instrumentResolver;
    private final DividendRepository dividendRepository;
    private final SecurityRepository securityRepository;
    private final PositionRepository positionRepository;
    private final DividendLoaderService dividendLoaderService;
    private final DividendSyncService self;

    public DividendSyncService(TinvestApi tinvestApi,
                               TinvestResilience tinvestResilience,
                               TinvestProperties tinvestProperties,
                               TinvestInstrumentResolver instrumentResolver,
                               DividendRepository dividendRepository,
                               SecurityRepository securityRepository,
                               PositionRepository positionRepository,
                               @Lazy DividendLoaderService dividendLoaderService,
                               @Lazy DividendSyncService self) {
        this.tinvestApi = tinvestApi;
        this.tinvestResilience = tinvestResilience;
        this.tinvestProperties = tinvestProperties;
        this.instrumentResolver = instrumentResolver;
        this.dividendRepository = dividendRepository;
        this.securityRepository = securityRepository;
        this.positionRepository = positionRepository;
        this.dividendLoaderService = dividendLoaderService;
        this.self = self;
    }

    // Runs on the same background executor the nightly job uses for history loading, so
    // application startup does not block on T-Invest round-trips (see MarketDataService's own
    // onApplicationReady for the same pattern).
    @Async("historyLoaderExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (isTokenBlank()) {
            log.warn("TINVEST_TOKEN не задан — дивиденды не синхронизируются");
            return;
        }
        try {
            syncOnStartup();
        } catch (Exception e) {
            log.warn("Синхронизация дивидендов на старте не выполнена: {}", e.getMessage());
        }
    }

    // Nightly job (see MarketDataRefreshScheduler): every active ticker, regardless of when it
    // was last synced.
    public void syncNightly() {
        if (isTokenBlank()) {
            return;
        }
        runBatch(positionRepository.findActiveTickers());
    }

    // Startup only: securities never synced, or last synced more than STARTUP_STALE_HOURS ago —
    // so a redeploy does not wait until the nightly job to show fresh data, without re-fetching
    // everything that was already refreshed a moment ago.
    public void syncOnStartup() {
        if (isTokenBlank()) {
            return;
        }
        List<String> activeTickers = positionRepository.findActiveTickers();
        if (activeTickers.isEmpty()) {
            return;
        }
        // Bulk-loaded once here instead of a findById per ticker — syncDividends (via
        // self.loadSecurity) still reloads a security it actually ends up syncing, but this
        // avoids the second findById per ticker just to decide whether to sync it at all
        // (plan point 11).
        Instant staleThreshold = Instant.now().minus(STARTUP_STALE_HOURS, ChronoUnit.HOURS);
        Map<String, Security> securitiesByTicker = securityRepository.findAllById(activeTickers).stream()
                .collect(Collectors.toMap(Security::getTicker, Function.identity()));
        List<String> tickers = activeTickers.stream()
                .filter(ticker -> isStaleOrNeverSynced(securitiesByTicker.get(ticker), staleThreshold))
                .toList();
        runBatch(tickers);
    }

    // Triggers async dividend sync via a separate @Async proxy bean to avoid calling @Async from
    // within the same transaction — first encounter of a newly traded ticker (see TransactionService).
    public void syncDividendsAsync(String ticker) {
        dividendLoaderService.loadAsync(ticker);
    }

    // Not @Transactional: instrumentResolver.resolve and fetchDividends below both make network
    // calls to T-Invest (through TinvestResilience — up to 3 retries and a 429 pause of up to
    // 60s each) — a transaction spanning them would hold a connection from the 5-connection pool
    // for that whole time. DB work stays in the two short transactions either side of the
    // network calls, both reached through the self proxy so @Transactional actually applies
    // (plain self-invocation bypasses the Spring AOP proxy).
    public DividendSyncResult syncDividends(String ticker) {
        if (isTokenBlank()) {
            return DividendSyncResult.EMPTY;
        }
        Security security = self.loadSecurity(ticker);
        if (security == null) {
            return DividendSyncResult.EMPTY;
        }

        boolean resolved;
        try {
            resolved = security.getTinvestUid() != null || instrumentResolver.resolve(security);
        } catch (TinvestUnauthorizedException e) {
            return DividendSyncResult.EMPTY;
        }
        if (!resolved) {
            return DividendSyncResult.EMPTY;
        }

        // Computed once for this ticker's whole sync — fetchDividends/fetchCoupons use it for the
        // request window, mergeDividend/mergeCoupon for each row's status — instead of each
        // calling LocalDate.now() on its own (once per row, for a security with several of them).
        LocalDate today = LocalDate.now();
        if (DIVIDEND_ELIGIBLE_TYPES.contains(security.getType())) {
            return syncDividendPayouts(ticker, security, today);
        }
        if (COUPON_ELIGIBLE_TYPES.contains(security.getType())) {
            return syncCouponPayouts(ticker, security, today);
        }
        return DividendSyncResult.EMPTY;
    }

    private DividendSyncResult syncDividendPayouts(String ticker, Security security, LocalDate today) {
        List<TinvestDividend> dividends;
        try {
            dividends = fetchDividends(security, today);
        } catch (RuntimeException e) {
            log.warn("Не удалось получить дивиденды для {}: {}", ticker, e.getMessage());
            return DividendSyncResult.EMPTY;
        }
        return self.mergeAndPersist(ticker, dividends, today);
    }

    private DividendSyncResult syncCouponPayouts(String ticker, Security security, LocalDate today) {
        List<TinvestCoupon> coupons;
        try {
            coupons = fetchCoupons(security, today);
        } catch (RuntimeException e) {
            log.warn("Не удалось получить купоны для {}: {}", ticker, e.getMessage());
            return DividendSyncResult.EMPTY;
        }
        return self.mergeAndPersistCoupons(ticker, coupons, today);
    }

    @Transactional("investmentTransactionManager")
    public Security loadSecurity(String ticker) {
        return securityRepository.findById(ticker).orElse(null);
    }

    // Re-reads the Security by id inside this write transaction instead of reusing the instance
    // the caller loaded before the network round-trip: that instance sat detached for up to a
    // minute (429 backoff included), and saving it here would merge a stale snapshot over
    // whatever MarketDataService wrote to the same row (historyStatus, lastPriceUpdatedAt) in the
    // meantime — a lost update. Only dividendsSyncedAt is changed on the freshly loaded entity.
    @Transactional("investmentTransactionManager")
    public DividendSyncResult mergeAndPersist(String ticker, List<TinvestDividend> dividends, LocalDate today) {
        return mergeAndPersistPayouts(ticker, dividends.isEmpty(), "дивиденды",
                security -> dividends.stream().map(dto -> mergeDividend(security, dto, today)).toList());
    }

    // Same shape as mergeAndPersist, for coupons — kept as its own @Transactional entry point
    // (rather than a shared public method taking a pre-built outcome list) so the self-proxy
    // call from syncCouponPayouts actually goes through Spring's transactional advice.
    @Transactional("investmentTransactionManager")
    public DividendSyncResult mergeAndPersistCoupons(String ticker, List<TinvestCoupon> coupons, LocalDate today) {
        return mergeAndPersistPayouts(ticker, coupons.isEmpty(), "купоны",
                security -> coupons.stream().map(dto -> mergeCoupon(security, dto, today)).toList());
    }

    private DividendSyncResult mergeAndPersistPayouts(String ticker, boolean sourceEmpty, String payoutLabel,
                                                       Function<Security, List<MergeOutcome>> merger) {
        Security security = securityRepository.findById(ticker).orElse(null);
        if (security == null) {
            return DividendSyncResult.EMPTY;
        }
        boolean hadBefore = dividendRepository.existsBySecurity_Ticker(ticker);
        if (sourceEmpty && hadBefore) {
            log.warn("T-Invest не вернул {} для {}, хотя записи уже есть — оставляем как есть", payoutLabel, ticker);
        }

        int added = 0;
        int updated = 0;
        for (MergeOutcome outcome : merger.apply(security)) {
            if (outcome == MergeOutcome.INSERTED) {
                added++;
            } else if (outcome == MergeOutcome.UPDATED) {
                updated++;
            }
        }
        security.setDividendsSyncedAt(Instant.now());
        securityRepository.save(security);
        return new DividendSyncResult(added, updated, sourceEmpty);
    }

    // A dividend is inserted as PAID only if its record date was already in the past at sync
    // time (see mergeDividend below); one still ANNOUNCED whose record date has since passed
    // needs this separate sweep — done as a single bulk UPDATE (see DividendRepository) instead
    // of loading every due row as an entity just to flip one field.
    @Transactional("investmentTransactionManager")
    public int markPastRecordDatesAsPaid() {
        int updated = dividendRepository.markPastRecordDatesAsPaid(LocalDate.now());
        if (updated > 0) {
            log.info("Переведено {} дивидендов из ANNOUNCED в PAID по прошедшей дате отсечки", updated);
        }
        return updated;
    }

    public boolean isSourceConfigured() {
        return !isTokenBlank() && securityRepository.existsByDividendsSyncedAtIsNotNull();
    }

    private void runBatch(List<String> tickers) {
        if (isTokenBlank() || tickers.isEmpty()) {
            return;
        }
        int added = 0;
        int updated = 0;
        int noSourceData = 0;
        int noChanges = 0;
        for (String ticker : tickers) {
            DividendSyncResult result = self.syncDividends(ticker);
            added += result.added();
            updated += result.updated();
            if (result.sourceEmpty()) {
                noSourceData++;
            } else if (result.added() == 0 && result.updated() == 0) {
                noChanges++;
            }
        }
        log.info("Дивиденды T-Invest: бумаг {}, добавлено {}, обновлено {}, источник пуст {}, без изменений {}",
                tickers.size(), added, updated, noSourceData, noChanges);
    }

    // A ticker with no Security row at all (never synced even once) counts as stale too.
    private boolean isStaleOrNeverSynced(Security security, Instant staleThreshold) {
        return security == null || security.getDividendsSyncedAt() == null
                || security.getDividendsSyncedAt().isBefore(staleThreshold);
    }

    private List<TinvestDividend> fetchDividends(Security security, LocalDate today) {
        LocalDate windowStart = today.minusMonths(WINDOW_BACK_MONTHS);
        LocalDate from = windowStart.isBefore(WINDOW_START_FLOOR) ? WINDOW_START_FLOOR : windowStart;
        LocalDate to = today.plusMonths(WINDOW_FORWARD_MONTHS);

        TinvestDividendsRequest request = new TinvestDividendsRequest(
                security.getTinvestUid(),
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.atStartOfDay(ZoneOffset.UTC).toInstant());
        TinvestDividendsResponse response = tinvestResilience.execute("getDividends",
                () -> tinvestApi.getDividends(request));
        return response.dividends() == null ? List.of() : response.dividends();
    }

    // Same request window as fetchDividends (plan point 6) — GetBondCoupons for a BOND/OFZ
    // instead of GetDividends for a STOCK/ETF.
    private List<TinvestCoupon> fetchCoupons(Security security, LocalDate today) {
        LocalDate windowStart = today.minusMonths(WINDOW_BACK_MONTHS);
        LocalDate from = windowStart.isBefore(WINDOW_START_FLOOR) ? WINDOW_START_FLOOR : windowStart;
        LocalDate to = today.plusMonths(WINDOW_FORWARD_MONTHS);

        TinvestBondCouponsRequest request = new TinvestBondCouponsRequest(
                security.getTinvestUid(),
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.atStartOfDay(ZoneOffset.UTC).toInstant());
        TinvestBondCouponsResponse response = tinvestResilience.execute("getBondCoupons",
                () -> tinvestApi.getBondCoupons(request));
        return response.events() == null ? List.of() : response.events();
    }

    // Merge by (ticker, recordDate) — see the unique index backing existsBySecurity_TickerAndRecordDate.
    // A MANUAL row is never touched; a MOEX/TINVEST row is updated only if something actually
    // changed, so a repeated run with the same source data reports zero updates (see plan's QA
    // scenario 7). Status is intentionally left alone on update — it only ever moves forward via
    // markPastRecordDatesAsPaid, not through this merge.
    private MergeOutcome mergeDividend(Security security, TinvestDividend dto, LocalDate today) {
        if (dto.recordDate() == null || dto.dividendNet() == null) {
            return MergeOutcome.SKIPPED;
        }
        BigDecimal amount = dto.dividendNet().toAmount();
        if (amount == null) {
            return MergeOutcome.SKIPPED;
        }
        LocalDate recordDate = toApplicationDate(dto.recordDate());
        LocalDate paymentDate = dto.paymentDate() != null ? toApplicationDate(dto.paymentDate()) : null;
        String currency = dto.dividendNet().currency() != null
                ? dto.dividendNet().currency().toUpperCase() : "RUB";
        amount = amount.setScale(4, RoundingMode.HALF_UP);
        return mergePayout(security, recordDate, paymentDate, amount, currency, PayoutKind.DIVIDEND, today);
    }

    // couponDate is the payment date, fixDate its record-date analogue (falling back to the day
    // before couponDate when T-Invest left it out, plan point 7). A future coupon T-Invest has
    // not announced an amount for yet (payOneBond = 0, couponDate not yet reached) is skipped
    // rather than saved as a zero payout that would never get corrected once announced.
    private MergeOutcome mergeCoupon(Security security, TinvestCoupon dto, LocalDate today) {
        if (dto.couponDate() == null || dto.payOneBond() == null) {
            return MergeOutcome.SKIPPED;
        }
        BigDecimal amount = dto.payOneBond().toAmount();
        if (amount == null) {
            return MergeOutcome.SKIPPED;
        }
        LocalDate couponDate = toApplicationDate(dto.couponDate());
        if (amount.compareTo(BigDecimal.ZERO) == 0 && !couponDate.isBefore(today)) {
            return MergeOutcome.SKIPPED;
        }
        LocalDate recordDate = dto.fixDate() != null ? toApplicationDate(dto.fixDate()) : couponDate.minusDays(1);
        String currency = dto.payOneBond().currency() != null
                ? dto.payOneBond().currency().toUpperCase() : "RUB";
        amount = amount.setScale(4, RoundingMode.HALF_UP);
        return mergePayout(security, recordDate, couponDate, amount, currency, PayoutKind.COUPON, today);
    }

    private MergeOutcome mergePayout(Security security, LocalDate recordDate, LocalDate paymentDate, BigDecimal amount,
                                     String currency, PayoutKind kind, LocalDate today) {
        Optional<Dividend> existingOpt = dividendRepository.findBySecurity_TickerAndRecordDate(
                security.getTicker(), recordDate);
        if (existingOpt.isEmpty()) {
            DividendStatus status = recordDate.isBefore(today) ? DividendStatus.PAID : DividendStatus.ANNOUNCED;
            Dividend dividend = Dividend.builder()
                    .security(security)
                    .recordDate(recordDate)
                    .paymentDate(paymentDate)
                    .amountPerShare(amount)
                    .currency(currency)
                    .status(status)
                    .source(DividendSource.TINVEST)
                    .kind(kind)
                    .build();
            dividendRepository.save(dividend);
            return MergeOutcome.INSERTED;
        }

        Dividend existing = existingOpt.get();
        if (existing.getSource() == DividendSource.MANUAL) {
            return MergeOutcome.SKIPPED;
        }
        boolean changed = existing.getAmountPerShare().compareTo(amount) != 0
                || !Objects.equals(existing.getCurrency(), currency)
                || !Objects.equals(existing.getPaymentDate(), paymentDate)
                || existing.getSource() != DividendSource.TINVEST
                || existing.getKind() != kind;
        if (!changed) {
            return MergeOutcome.SKIPPED;
        }
        existing.setAmountPerShare(amount);
        existing.setCurrency(currency);
        existing.setPaymentDate(paymentDate);
        existing.setSource(DividendSource.TINVEST);
        existing.setKind(kind);
        dividendRepository.save(existing);
        return MergeOutcome.UPDATED;
    }

    // The record/payment date is a calendar day, and every other day-boundary calculation in the
    // app (HoldingsOnDateService, AnalyticsService) uses ZoneId.systemDefault() (Europe/Moscow,
    // set by App.main) — using UTC here instead would only coincide with those as long as the
    // source's timestamp happens to be midnight UTC; any other time of day would shift the date
    // by a day relative to the ownership-window cutoffs computed elsewhere (plan point 9).
    private LocalDate toApplicationDate(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private boolean isTokenBlank() {
        return tinvestProperties.getToken() == null || tinvestProperties.getToken().isBlank();
    }

    private enum MergeOutcome {
        INSERTED, UPDATED, SKIPPED
    }

    // sourceEmpty distinguishes "T-Invest returned no dividend rows at all for this ticker" from
    // "it returned rows, but every one of them already matched what's in the DB" — both show up
    // as added=0/updated=0, but runBatch's log line must not conflate them (plan point 8).
    public record DividendSyncResult(int added, int updated, boolean sourceEmpty) {
        static final DividendSyncResult EMPTY = new DividendSyncResult(0, 0, false);
    }
}
