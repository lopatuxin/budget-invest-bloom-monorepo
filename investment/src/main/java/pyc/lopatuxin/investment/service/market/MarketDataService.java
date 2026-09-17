package pyc.lopatuxin.investment.service.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.client.moex.MoexIssClient;
import pyc.lopatuxin.investment.client.moex.MoexUnavailableException;
import pyc.lopatuxin.investment.dto.response.MoexCandleDto;
import pyc.lopatuxin.investment.dto.response.MoexSecurityDto;
import pyc.lopatuxin.investment.dto.response.MoexSnapshotDto;
import pyc.lopatuxin.investment.config.MoexProperties;
import pyc.lopatuxin.investment.dto.request.SearchCategory;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.PriceSnapshot;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.repository.PriceSnapshotRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;
import pyc.lopatuxin.investment.dto.response.SnapshotResult;
import pyc.lopatuxin.investment.service.BondPricing;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MarketDataService {

    // Slack around the expected three-year backfill start: MOEX has no trades on a given
    // calendar date around weekends/holidays, so the earliest saved row is never expected to
    // land exactly on "now minus three years".
    private static final int HISTORY_FRONT_GAP_TOLERANCE_DAYS = 10;

    private final MoexIssClient moexIssClient;
    private final SecurityRepository securityRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MoexProperties moexProperties;
    private final HistoryLoaderService historyLoaderService;
    private final BondPricing bondPricing;
    private final MarketDataService self;

    public MarketDataService(MoexIssClient moexIssClient,
                             SecurityRepository securityRepository,
                             PriceSnapshotRepository priceSnapshotRepository,
                             PriceHistoryRepository priceHistoryRepository,
                             MoexProperties moexProperties,
                             @Lazy HistoryLoaderService historyLoaderService,
                             BondPricing bondPricing,
                             @Lazy MarketDataService self) {
        this.moexIssClient = moexIssClient;
        this.securityRepository = securityRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.moexProperties = moexProperties;
        this.historyLoaderService = historyLoaderService;
        this.bondPricing = bondPricing;
        this.self = self;
    }

    public Security ensureSecurity(String ticker, SecurityType fallbackType) {
        String normalizedTicker = ticker.toUpperCase();
        Optional<Security> existing = securityRepository.findById(normalizedTicker);
        if (existing.isPresent()) {
            return existing.get();
        }
        MoexSecurityDto moexDto = null;
        try {
            moexDto = moexIssClient.fetchSecurity(normalizedTicker).orElse(null);
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for ticker {}, saving as PENDING", normalizedTicker);
        }
        BigDecimal nominal = moexDto != null && bondPricing.isQuotedAsPercentOfPar(moexDto.securityType())
                ? fetchNominal(normalizedTicker).orElse(null)
                : null;
        return self.persistNewSecurity(normalizedTicker, moexDto, fallbackType, nominal);
    }

    @Transactional("investmentTransactionManager")
    public Security persistNewSecurity(String ticker, MoexSecurityDto moexDto, SecurityType fallbackType, BigDecimal nominal) {
        return securityRepository.findById(ticker).orElseGet(() -> {
            Security security = moexDto != null
                    ? buildReadySecurity(ticker, moexDto)
                    : buildPendingSecurity(ticker, fallbackType);
            security.setNominal(nominal);
            return securityRepository.save(security);
        });
    }

    // A bond's nominal is needed from the moment it is created (plan point 1) — otherwise every
    // quote conversion until the next scheduled snapshot refresh silently defaults to 1000₽
    // (BondPricing) instead of using the real FACEVALUE the exchange already has. Fetched before
    // persistNewSecurity so the exchange call does not hold a pooled DB connection.
    private Optional<BigDecimal> fetchNominal(String ticker) {
        try {
            return Optional.ofNullable(moexIssClient.fetchSnapshots(List.of(ticker)).get(ticker))
                    .map(MoexSnapshotDto::faceValue);
        } catch (MoexUnavailableException e) {
            return Optional.empty();
        }
    }

    // Raw exchange quote before ruble conversion, plus FACEVALUE (plan point 1) so the trade-dialog
    // snapshot below can convert a bond found only by exchange search — not yet in
    // investment.securities — the same way it converts one already on file.
    private record RawQuote(BigDecimal lastPrice, BigDecimal previousClose, Instant fetchedAt, boolean stale,
                            BigDecimal accruedInterest, BigDecimal faceValue) {
        static RawQuote empty() {
            return new RawQuote(null, null, null, true, null, null);
        }
    }

    // The one caller (MarketDataController's trade-dialog snapshot endpoint) needs the price it
    // substitutes into the form already in rubles, so the conversion happens here rather than in
    // every other getSnapshot(s) caller — those feed PortfolioGroupingService/ProjectionService/
    // SecurityPageService, which apply BondPricing themselves against the position's/security's
    // own nominal (see BondPricingTest, PortfolioGroupingServiceUnitTest).
    public SnapshotResult getSnapshotReadOnly(String ticker) {
        RawQuote raw = fetchRawQuoteReadOnly(ticker);
        Security security = securityRepository.findById(ticker).orElse(null);
        return security != null ? convertKnownToRubles(security, raw) : convertUnknownToRubles(ticker, raw);
    }

    private RawQuote fetchRawQuoteReadOnly(String ticker) {
        Optional<PriceSnapshot> dbSnapshot = priceSnapshotRepository.findById(ticker);
        if (dbSnapshot.isPresent() && !isStale(dbSnapshot.get())) {
            return toRawQuote(dbSnapshot.get(), false);
        }
        try {
            Map<String, MoexSnapshotDto> fetched = moexIssClient.fetchSnapshots(List.of(ticker));
            MoexSnapshotDto dto = fetched.get(ticker);
            if (dto != null) {
                return new RawQuote(dto.lastPrice(), dto.previousClose(), Instant.now(), false,
                        dto.accruedInterest(), dto.faceValue());
            }
            return dbSnapshot.map(s -> toRawQuote(s, true)).orElseGet(RawQuote::empty);
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for snapshot {} (read-only)", ticker);
            return dbSnapshot.map(s -> toRawQuote(s, true)).orElseGet(RawQuote::empty);
        }
    }

    private RawQuote toRawQuote(PriceSnapshot snapshot, boolean stale) {
        return new RawQuote(snapshot.getLastPrice(), snapshot.getPreviousClose(), snapshot.getFetchedAt(), stale,
                snapshot.getAccruedInterest(), null);
    }

    private SnapshotResult convertKnownToRubles(Security security, RawQuote raw) {
        BigDecimal last = bondPricing.quotedToRubles(security, raw.lastPrice());
        BigDecimal previous = bondPricing.quotedToRubles(security, raw.previousClose());
        return new SnapshotResult(last, previous, raw.fetchedAt(), raw.stale(), raw.accruedInterest());
    }

    // A bond/OFZ found only via exchange search (plan point 3, not yet in investment.securities):
    // the exchange's own bond-board response already carried FACEVALUE when the ticker is quoted
    // as percent-of-par (see MoexResponseParser.parseBondFields) — its absence means either the
    // security is not a bond at all (already-ruble quote, pass through unchanged) or it is a bond
    // the exchange has not returned a nominal for yet, resolved with fetchSecurity's own
    // classification and BondPricing's default-1000 fallback (plan point 17).
    private SnapshotResult convertUnknownToRubles(String ticker, RawQuote raw) {
        if (raw.faceValue() == null && !isQuotedAsPercentOfParTicker(ticker)) {
            return new SnapshotResult(raw.lastPrice(), raw.previousClose(), raw.fetchedAt(), raw.stale(), raw.accruedInterest());
        }
        BigDecimal last = bondPricing.quotedToRubles(SecurityType.BOND, ticker, raw.faceValue(), raw.lastPrice());
        BigDecimal previous = bondPricing.quotedToRubles(SecurityType.BOND, ticker, raw.faceValue(), raw.previousClose());
        return new SnapshotResult(last, previous, raw.fetchedAt(), raw.stale(), raw.accruedInterest());
    }

    private boolean isQuotedAsPercentOfParTicker(String ticker) {
        try {
            return moexIssClient.fetchSecurity(ticker)
                    .map(dto -> bondPricing.isQuotedAsPercentOfPar(dto.securityType()))
                    .orElse(false);
        } catch (MoexUnavailableException e) {
            return false;
        }
    }

    @Cacheable(value = "moexSnapshots", key = "#ticker")
    public SnapshotResult getSnapshot(String ticker) {
        Optional<PriceSnapshot> dbSnapshot = priceSnapshotRepository.findById(ticker);
        if (dbSnapshot.isPresent() && !isStale(dbSnapshot.get())) {
            return toSnapshotResult(dbSnapshot.get(), false);
        }
        try {
            Map<String, MoexSnapshotDto> fetched = moexIssClient.fetchSnapshots(List.of(ticker));
            MoexSnapshotDto dto = fetched.get(ticker);
            if (dto != null) {
                PriceSnapshot snapshot = upsertSnapshotRetrying(ticker, dto);
                if (snapshot != null) {
                    return toSnapshotResult(snapshot, false);
                }
            }
            return dbSnapshot.map(s -> toSnapshotResult(s, true))
                    .orElse(new SnapshotResult(null, null, null, true));
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for snapshot {}", ticker);
            return dbSnapshot.map(s -> toSnapshotResult(s, true))
                    .orElse(new SnapshotResult(null, null, null, true));
        }
    }

    public Map<String, SnapshotResult> getSnapshots(Collection<String> tickers) {
        if (tickers.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, PriceSnapshot> dbSnapshotByTicker = priceSnapshotRepository.findAllById(tickers).stream()
                .collect(Collectors.toMap(PriceSnapshot::getTicker, s -> s));

        Map<String, SnapshotResult> result = new HashMap<>();
        List<String> staleTickers = new ArrayList<>();
        for (String ticker : tickers) {
            PriceSnapshot snapshot = dbSnapshotByTicker.get(ticker);
            if (snapshot != null && !isStale(snapshot)) {
                result.put(ticker, toSnapshotResult(snapshot, false));
            } else {
                staleTickers.add(ticker);
            }
        }
        fetchAndUpsertInto(result, staleTickers);
        return result;
    }

    // Forces a MOEX round-trip and upsert for every given ticker, ignoring the DB snapshot's
    // TTL — used only by the scheduled refresh (see MarketDataRefreshScheduler). That job's
    // own cron period equals the TTL, so a snapshot upserted seconds into one run is still
    // "fresh" by isStale's clock when the next run starts exactly one period later; going
    // through getSnapshots there would silently skip it and quotes would only actually change
    // every other run instead of every run.
    public Map<String, SnapshotResult> refreshSnapshots(Collection<String> tickers) {
        if (tickers.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, SnapshotResult> result = new HashMap<>();
        fetchAndUpsertInto(result, new ArrayList<>(tickers));
        return result;
    }

    private void fetchAndUpsertInto(Map<String, SnapshotResult> result, List<String> tickers) {
        if (tickers.isEmpty()) {
            return;
        }
        try {
            Map<String, MoexSnapshotDto> fetched = moexIssClient.fetchSnapshots(tickers);
            for (String ticker : tickers) {
                MoexSnapshotDto dto = fetched.get(ticker);
                PriceSnapshot snapshot = dto != null ? upsertSnapshotRetrying(ticker, dto) : null;
                result.put(ticker, snapshot != null ? toSnapshotResult(snapshot, false) : resolveFromDbOrStale(ticker));
            }
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for batch snapshots");
            for (String ticker : tickers) {
                result.put(ticker, resolveFromDbOrStale(ticker));
            }
        }
    }

    @Cacheable(value = "moexSecurities", key = "'list:' + (#category != null ? #category.name() : 'ALL')")
    public List<MoexSecurityDto> listSecurities(SearchCategory category) {
        try {
            List<MoexSecurityDto> result = new ArrayList<>();
            if (category == null || category == SearchCategory.STOCKS) {
                result.addAll(moexIssClient.listBoardSecurities("shares", "TQBR", SecurityType.STOCK));
                result.addAll(moexIssClient.listBoardSecurities("shares", "TQTF", SecurityType.ETF));
            }
            if (category == null || category == SearchCategory.BONDS) {
                result.addAll(moexIssClient.listBoardSecurities("bonds", "TQOB", SecurityType.BOND));
            }
            return result;
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for listSecurities category={}", category);
            return Collections.emptyList();
        }
    }

    @Cacheable(value = "moexSecurities", key = "#query + ':' + (#category != null ? #category.name() : 'ALL')")
    public List<MoexSecurityDto> search(String query, SearchCategory category) {
        try {
            List<MoexSecurityDto> results = moexIssClient.searchSecurities(query);
            if (category == null) {
                return results;
            }
            Set<SecurityType> allowed = resolveTypes(category);
            return results.stream()
                    .filter(r -> allowed.contains(r.securityType()))
                    .toList();
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for search query: {}", query);
            return Collections.emptyList();
        }
    }

    private Set<SecurityType> resolveTypes(SearchCategory category) {
        return switch (category) {
            case STOCKS -> Set.of(SecurityType.STOCK, SecurityType.ETF);
            case BONDS -> Set.of(SecurityType.BOND, SecurityType.OFZ);
        };
    }

    public void ensureHistory(String ticker) {
        Optional<Security> secOpt = securityRepository.findById(ticker);
        if (secOpt.isEmpty()) {
            log.warn("Security {} not found, skipping history load", ticker);
            return;
        }
        Security security = secOpt.get();
        LocalDate from = resolveHistoryFrom(ticker, security);
        LocalDate to = LocalDate.now();
        if (!from.isAfter(to)) {
            loadHistory(ticker, from, to);
        }
    }

    // READY tickers that already have a full three-year history only need the candles saved
    // after their last trade date (nightly catch-up); everything else (a brand-new ticker, a
    // READY one without a history row yet, or a READY one whose earliest saved row does not
    // reach back far enough — a hole left by an earlier incomplete backfill) gets the full
    // three-year range, so a front gap is closed instead of being permanently stuck before the
    // earliest saved date.
    private LocalDate resolveHistoryFrom(String ticker, Security security) {
        LocalDate expectedFrom = LocalDate.now().minusYears(3);
        if (security.getHistoryStatus() == HistoryStatus.READY) {
            Optional<LocalDate> lastSaved = priceHistoryRepository.findFirstByTickerOrderByTradeDateDesc(ticker)
                    .map(PriceHistory::getTradeDate);
            Optional<LocalDate> earliestSaved = priceHistoryRepository.findFirstByTickerOrderByTradeDateAsc(ticker)
                    .map(PriceHistory::getTradeDate);
            boolean coversFullRange = earliestSaved.isPresent()
                    && !earliestSaved.get().isAfter(expectedFrom.plusDays(HISTORY_FRONT_GAP_TOLERANCE_DAYS));
            if (lastSaved.isPresent() && coversFullRange) {
                return lastSaved.get().plusDays(1);
            }
        }
        return expectedFrom;
    }

    private void loadHistory(String ticker, LocalDate from, LocalDate to) {
        try {
            List<MoexCandleDto> candles = moexIssClient.fetchHistory(ticker, from, to);
            List<PriceHistory> records = candles.stream()
                    .map(c -> PriceHistory.builder()
                            .ticker(c.ticker())
                            .tradeDate(c.tradeDate())
                            .open(c.open())
                            .close(c.close())
                            .high(c.high())
                            .low(c.low())
                            .volume(c.volume())
                            .build())
                    .toList();
            self.saveHistoryAndUpdateStatus(ticker, records);
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for history {} ({} — {})", ticker, from, to);
        }
    }

    @Transactional("investmentTransactionManager")
    public void saveHistoryAndUpdateStatus(String ticker, List<PriceHistory> records) {
        priceHistoryRepository.saveAll(records);
        if (records.isEmpty() && !priceHistoryRepository.existsByTicker(ticker)) {
            // MOEX returned nothing at all for this ticker, so it still has no history — it must
            // not be marked READY (that would tell self-healing/the page it is fine); leave it
            // for the next self-healing pass to retry.
            return;
        }
        securityRepository.findById(ticker).ifPresent(s -> {
            s.setHistoryStatus(HistoryStatus.READY);
            securityRepository.save(s);
        });
    }

    public void triggerHistoryAsync(String ticker) {
        historyLoaderService.loadAsync(ticker);
    }

    public HistoryStatus getSecurityHistoryStatus(String ticker) {
        return securityRepository.findById(ticker)
                .map(Security::getHistoryStatus)
                .orElse(HistoryStatus.PENDING);
    }

    private Security buildReadySecurity(String ticker, MoexSecurityDto dto) {
        Security security = Security.builder().ticker(ticker).historyStatus(HistoryStatus.READY).build();
        applyMoexInfo(security, ticker, dto);
        return security;
    }

    // Fills the security dictionary fields (name, type, sector, board, currency, maturity date)
    // from a MOEX response; shared by first-time creation (buildReadySecurity) and PENDING
    // self-healing.
    private void applyMoexInfo(Security security, String ticker, MoexSecurityDto dto) {
        // If MOEX did not return a sector, resolve from local dictionary
        String sector = dto.sector() != null
                ? dto.sector()
                : SectorDefaults.resolveSector(ticker, dto.securityType());
        security.setBoardId(dto.boardId());
        security.setName(dto.name() != null ? dto.name() : ticker);
        security.setType(dto.securityType());
        security.setSector(sector);
        security.setCurrency(dto.currency());
        security.setMaturityDate(dto.maturityDate());
    }

    // Self-healing for securities saved as PENDING while MOEX was unavailable: re-fetches
    // the dictionary entry and history so a bad first attempt does not stay broken forever.
    public void healPendingSecurities() {
        List<Security> pending = securityRepository.findAllByHistoryStatus(HistoryStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Самолечение PENDING: найдено {} бумаг", pending.size());
        int healed = 0;
        for (Security security : pending) {
            String ticker = security.getTicker();
            try {
                if (healPendingSecurity(ticker)) {
                    healed++;
                }
            } catch (Exception e) {
                log.warn("Самолечение {} завершилось ошибкой: {}", ticker, e.getMessage());
            }
        }
        log.info("Самолечение PENDING завершено: восстановлено {} из {}", healed, pending.size());
    }

    // Counts a security as healed only once it actually reached READY: applyHealedSecurityInfo
    // fills the dictionary fields but leaves the status alone, and ensureHistory below can
    // still bail out on MoexUnavailableException, leaving the security PENDING.
    private boolean healPendingSecurity(String ticker) {
        Optional<MoexSecurityDto> moexDto;
        try {
            moexDto = moexIssClient.fetchSecurity(ticker);
        } catch (MoexUnavailableException e) {
            log.warn("MOEX недоступна, самолечение {} отложено", ticker);
            return false;
        }
        if (moexDto.isEmpty() || !self.applyHealedSecurityInfo(ticker, moexDto.get())) {
            return false;
        }
        ensureHistory(ticker);
        return securityRepository.findById(ticker)
                .map(s -> s.getHistoryStatus() == HistoryStatus.READY)
                .orElse(false);
    }

    @Transactional("investmentTransactionManager")
    public boolean applyHealedSecurityInfo(String ticker, MoexSecurityDto dto) {
        Security security = securityRepository.findById(ticker).orElse(null);
        if (security == null || security.getHistoryStatus() != HistoryStatus.PENDING) {
            return false;
        }
        applyMoexInfo(security, ticker, dto);
        securityRepository.save(security);
        return true;
    }

    // Securities created while MoexResponseParser looked for the trading board in a block that
    // /iss/securities/{ticker}.json never returns were saved without board_id, and nothing else
    // rewrites a READY entry's dictionary fields (PENDING ones get them from
    // healPendingSecurities). MOEX is asked outside any transaction, each hit saved in its own.
    public void backfillMissingBoards() {
        List<Security> missing = securityRepository.findByBoardIdIsNullAndHistoryStatus(HistoryStatus.READY);
        int updated = 0;
        for (Security security : missing) {
            String ticker = security.getTicker();
            try {
                Optional<String> boardId = moexIssClient.fetchSecurity(ticker).map(MoexSecurityDto::boardId);
                if (boardId.isPresent() && self.applyBoardId(ticker, boardId.get())) {
                    updated++;
                }
            } catch (MoexUnavailableException e) {
                log.warn("MOEX недоступна, заполнение площадок отложено до следующего старта");
                break;
            } catch (Exception e) {
                log.warn("Площадку {} заполнить не удалось: {}", ticker, e.getMessage());
            }
        }
        log.info("Заполнение площадок: обновлено {} из {} бумаг", updated, missing.size());
    }

    @Transactional("investmentTransactionManager")
    public boolean applyBoardId(String ticker, String boardId) {
        Security security = securityRepository.findById(ticker).orElse(null);
        if (security == null || security.getBoardId() != null) {
            return false;
        }
        security.setBoardId(boardId);
        securityRepository.save(security);
        return true;
    }

    private Security buildPendingSecurity(String ticker, SecurityType fallbackType) {
        return Security.builder()
                .ticker(ticker)
                .name(ticker)
                .type(fallbackType)
                .sector(SectorDefaults.resolveSector(ticker, fallbackType))
                .historyStatus(HistoryStatus.PENDING)
                .build();
    }

    // Own transaction, independent of the caller: none of the getSnapshot(s) callers hold a
    // surrounding transaction any more (it would pin a Hikari connection for as long as MOEX
    // takes to answer), so most calls here have no outer transaction to join at all. REQUIRES_NEW
    // still matters: it commits the upsert on its own regardless of what (if anything) called
    // it, and saveAndFlush forces the INSERT/UPDATE to run — and any constraint violation to
    // surface — synchronously here, instead of at commit time after the method has already
    // returned, so upsertSnapshotRetrying below can actually catch it.
    @Transactional(value = "investmentTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public PriceSnapshot upsertSnapshot(String ticker, MoexSnapshotDto dto) {
        if (!securityRepository.existsById(ticker)) {
            return null;
        }
        if (dto.faceValue() != null) {
            updateNominalIfChanged(ticker, dto.faceValue());
        }
        Optional<PriceSnapshot> existing = priceSnapshotRepository.findById(ticker);
        // LAST is genuinely absent outside trading hours, on weekends and for illiquid papers.
        // An existing snapshot's lastPrice is the market's last real trade and must not be
        // wiped just because this particular poll came back empty — leaving it untouched is
        // more honest than persisting null over a known price. previousClose and fetchedAt are
        // still updated when MOEX did return a previousClose: a ticker that stops trading for a
        // while keeps getting a fresh previousClose (the prior session's close shifts forward
        // even without a new trade) and a fresh fetchedAt, so isStale below does not flag an
        // otherwise healthy, responsive poll as stale just because LAST stayed empty. last_price
        // is nullable for exactly this case (see the 012-price-snapshots-last-price-nullable
        // changelog).
        if (dto.lastPrice() == null) {
            if (dto.previousClose() == null) {
                return null;
            }
            PriceSnapshot snapshot = existing.orElseGet(() -> PriceSnapshot.builder().ticker(ticker).build());
            snapshot.setPreviousClose(dto.previousClose());
            snapshot.setFetchedAt(Instant.now());
            if (dto.accruedInterest() != null) {
                snapshot.setAccruedInterest(dto.accruedInterest());
            }
            return priceSnapshotRepository.saveAndFlush(snapshot);
        }
        PriceSnapshot snapshot = existing.orElseGet(() -> PriceSnapshot.builder().ticker(ticker).build());
        snapshot.setLastPrice(dto.lastPrice());
        snapshot.setPreviousClose(dto.previousClose());
        snapshot.setFetchedAt(Instant.now());
        if (dto.accruedInterest() != null) {
            snapshot.setAccruedInterest(dto.accruedInterest());
        }
        return priceSnapshotRepository.saveAndFlush(snapshot);
    }

    // Nominal changes only when an amortizing bond's next FACEVALUE differs from what is stored
    // (plan point 1) — most polls skip the write entirely. A separate lookup (rather than reusing
    // the existsById check above) because it is only ever needed for a BOND/OFZ that actually
    // returned FACEVALUE.
    private void updateNominalIfChanged(String ticker, BigDecimal nominal) {
        securityRepository.findById(ticker).ifPresent(security -> {
            if (security.getNominal() == null || nominal.compareTo(security.getNominal()) != 0) {
                security.setNominal(nominal);
                securityRepository.save(security);
            }
        });
    }

    // upsertSnapshot is check-then-act (findById, then insert/update) with no locking, so two
    // concurrent requests upserting the same brand-new ticker can both pass the check and race
    // on the primary key; the loser's INSERT fails a unique-constraint violation. Postgres
    // aborts that transaction outright, so the retry must be a fresh REQUIRES_NEW call (a new
    // connection, a new attempt against the row the winner just committed) rather than a second
    // attempt inside the same failed one. Only that specific race is worth retrying: any other
    // constraint violation (e.g. a NOT NULL check) fails the exact same way a second time, so
    // it is rethrown instead of being retried uselessly.
    private PriceSnapshot upsertSnapshotRetrying(String ticker, MoexSnapshotDto dto) {
        try {
            return self.upsertSnapshot(ticker, dto);
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicateKeyViolation(e)) {
                throw e;
            }
            return self.upsertSnapshot(ticker, dto);
        }
    }

    private boolean isDuplicateKeyViolation(DataIntegrityViolationException e) {
        Throwable rootCause = e.getRootCause();
        return rootCause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState());
    }

    private SnapshotResult resolveFromDbOrStale(String ticker) {
        return priceSnapshotRepository.findById(ticker)
                .map(s -> toSnapshotResult(s, true))
                .orElse(new SnapshotResult(null, null, null, true));
    }

    private boolean isStale(PriceSnapshot snapshot) {
        return snapshot.getFetchedAt().isBefore(
                Instant.now().minus(moexProperties.getSnapshotTtlMinutes(), ChronoUnit.MINUTES)
        );
    }

    private SnapshotResult toSnapshotResult(PriceSnapshot snapshot, boolean stale) {
        return new SnapshotResult(snapshot.getLastPrice(), snapshot.getPreviousClose(), snapshot.getFetchedAt(), stale,
                snapshot.getAccruedInterest());
    }

    // Runs on the same background executor the nightly job uses for history loading, so
    // application startup does not block on MOEX round-trips (or timeouts, while the exchange
    // is unreachable); each step is guarded on its own so one failure cannot skip the others.
    @Async("historyLoaderExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            self.healPendingSecurities();
        } catch (Exception e) {
            log.warn("Самолечение PENDING на старте не выполнено: {}", e.getMessage());
        }
        try {
            self.backfillMissingSectors();
        } catch (Exception e) {
            log.warn("Донастройка секторов на старте не выполнена: {}", e.getMessage());
        }
        try {
            self.backfillMissingBoards();
        } catch (Exception e) {
            log.warn("Заполнение площадок на старте не выполнено: {}", e.getMessage());
        }
    }

    @Transactional("investmentTransactionManager")
    public void backfillMissingSectors() {
        List<Security> missing = securityRepository.findBySectorIsNull();
        int updated = 0;
        for (Security security : missing) {
            String sector = SectorDefaults.resolveSector(security.getTicker(), security.getType());
            if (sector != null) {
                security.setSector(sector);
                securityRepository.save(security);
                updated++;
            }
        }
        log.info("Sector backfill: updated {} securities", updated);
        reclassifyMistypedOfz();
    }

    private void reclassifyMistypedOfz() {
        List<Security> candidates = securityRepository.findByTypeAndSector(
                SecurityType.BOND, SectorDefaults.CORPORATE_BONDS);
        int fixed = 0;
        for (Security security : candidates) {
            if (!isOfzTicker(security.getTicker())) {
                continue;
            }
            try {
                Optional<MoexSecurityDto> dto = moexIssClient.fetchSecurity(security.getTicker());
                if (dto.isPresent() && dto.get().securityType() == SecurityType.OFZ) {
                    security.setType(SecurityType.OFZ);
                    security.setSector(SectorDefaults.GOVERNMENT_BONDS);
                    securityRepository.save(security);
                    fixed++;
                }
            } catch (Exception e) {
                log.warn("Failed to reclassify OFZ candidate {}: {}", security.getTicker(), e.getMessage());
            }
        }
        log.info("OFZ reclassification: fixed {} securities", fixed);
    }

    // OFZ tickers follow the pattern SU + 5 digits + RMFS + 1 digit (e.g. SU26219RMFS4)
    private boolean isOfzTicker(String ticker) {
        return ticker != null && ticker.matches("SU\\d{5}RMFS\\d");
    }
}
