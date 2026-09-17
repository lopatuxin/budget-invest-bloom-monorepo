package pyc.lopatuxin.investment.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.investment.dto.response.PositionResponseDto;
import pyc.lopatuxin.investment.dto.response.SecurityEventDto;
import pyc.lopatuxin.investment.dto.response.SecurityEventKind;
import pyc.lopatuxin.investment.dto.response.SecurityEventPositionDto;
import pyc.lopatuxin.investment.dto.response.SecurityMarkerDto;
import pyc.lopatuxin.investment.dto.response.SecurityNextDividendDto;
import pyc.lopatuxin.investment.dto.response.SecurityPageDividendsDto;
import pyc.lopatuxin.investment.dto.response.SecurityPagePriceDto;
import pyc.lopatuxin.investment.dto.response.SecurityPageResponseDto;
import pyc.lopatuxin.investment.dto.response.SecurityPageResultDto;
import pyc.lopatuxin.investment.dto.response.SecurityPageSecurityDto;
import pyc.lopatuxin.investment.dto.response.SnapshotResult;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
import pyc.lopatuxin.investment.mapper.PositionMapper;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.TransactionRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds the "ownership history" page for a single ticker (security-page-redesign plan): price
 * with the user's own trade markers, one chronological timeline of trades and dividends, and the
 * "Итог" summary. One request, all arithmetic on the backend (plan's non-functional section).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityPageService {

    private static final RoundingMode RM = RoundingMode.HALF_UP;
    private static final int SCALE = 2;
    private static final BigDecimal ZERO2 = BigDecimal.ZERO.setScale(SCALE, RM);
    private static final String RUB = "RUB";
    private static final Set<SecurityEventKind> SELL_LIKE_KINDS = Set.of(SecurityEventKind.SELL, SecurityEventKind.REDEMPTION);

    // Same primary-then-tie-break rule as the "Мои операции" journal replay (TransactionService.
    // recalculatePosition): a Dividend has no creation timestamp, so it sorts after a transaction
    // that landed on the same day (nulls first before reversal — i.e. last once the whole chain
    // is reversed to "newest date, newest transaction, then dividends").
    private static final Comparator<SecurityEventDto> EVENT_ORDER = Comparator
            .comparing(SecurityEventDto::getDate)
            .thenComparing(SecurityEventDto::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .reversed();

    private final TransactionRepository transactionRepository;
    private final DividendRepository dividendRepository;
    private final PositionRepository positionRepository;
    private final PositionMapper positionMapper;
    private final MarketDataService marketDataService;
    private final PortfolioGroupingService portfolioGroupingService;
    private final HoldingsOnDateService holdingsOnDateService;
    private final DividendTaxCalculator dividendTaxCalculator;
    private final BondPricing bondPricing;

    // Not wrapped in a transaction, same as PortfolioService.getPortfolioPage: getSnapshots can
    // wait on MOEX, and an outer transaction would pin a Hikari connection for that round-trip
    // while MarketDataService.upsertSnapshot (REQUIRES_NEW) asks the pool for a second one. Every
    // association read below is fetched by its own query, so no open session is needed.
    public SecurityPageResponseDto getSecurityPage(UUID userId, String rawTicker) {
        return getSecurityPage(userId, rawTicker, LocalDate.now());
    }

    // Package-visible overload for SecurityPageServiceTest: a fixed "today" instead of
    // LocalDate.now(), same reason PortfolioService threads a single "today" through a whole
    // page build (plan point 3) — every dividend bucketing decision below judges the same day.
    SecurityPageResponseDto getSecurityPage(UUID userId, String rawTicker, LocalDate today) {
        String ticker = rawTicker.toUpperCase();
        List<Transaction> journalDesc = transactionRepository.findByUserIdAndTickerWithSecurity(userId, ticker);
        if (journalDesc.isEmpty()) {
            throw new EntityNotFoundException("Бумага не найдена в портфеле: " + ticker);
        }
        Security security = journalDesc.get(0).getSecurity();
        List<Transaction> journalAsc = ascendingByExecutedThenCreated(journalDesc);

        JournalReplay replay = replayJournal(journalAsc);
        TickerPricing pricing = priceTicker(userId, ticker);
        PositionResponseDto position = pricing.position();

        Map<String, List<Transaction>> journalByTicker = Map.of(ticker, journalAsc);
        List<Dividend> dividends = dividendRepository.findBySecurity_Ticker(ticker);
        DividendBuild dividendBuild = buildDividendEvents(dividends, journalByTicker, ticker, replay.quantity(), today);

        List<SecurityEventDto> events = combineAndSortEvents(replay.events(), dividendBuild.events());
        SecurityPageDividendsDto dividendsDto = buildDividendsDto(dividendBuild, position);
        SecurityPageResultDto result = buildResult(position, replay, dividendsDto);

        return SecurityPageResponseDto.builder()
                .security(toSecurityDto(security))
                .price(buildPriceDto(security, pricing.snapshot()))
                .position(position)
                .dividends(dividendsDto)
                .result(result)
                .transactionsCount(journalAsc.size())
                .buysCount(replay.buysCount())
                .sellsCount(replay.sellsCount())
                .markers(replay.markers())
                .events(events)
                .build();
    }

    private List<Transaction> ascendingByExecutedThenCreated(List<Transaction> journalDesc) {
        List<Transaction> sorted = new ArrayList<>(journalDesc);
        sorted.sort(Comparator.comparing(Transaction::getExecutedAt).thenComparing(Transaction::getCreatedAt));
        return sorted;
    }

    private List<SecurityEventDto> combineAndSortEvents(List<SecurityEventDto> transactionEvents,
                                                         List<SecurityEventDto> dividendEvents) {
        List<SecurityEventDto> combined = new ArrayList<>(transactionEvents.size() + dividendEvents.size());
        combined.addAll(transactionEvents);
        combined.addAll(dividendEvents);
        combined.sort(EVENT_ORDER);
        return combined;
    }

    // ---- trade journal replay: quantity, cost basis and average price after each BUY/SELL ----

    private record PositionState(BigDecimal quantity, BigDecimal costBasis) {
    }

    private record JournalReplay(List<SecurityEventDto> events, List<SecurityMarkerDto> markers,
                                 BigDecimal quantity, BigDecimal investedAllGross, BigDecimal realizedPnlTotal,
                                 int buysCount, int sellsCount) {
    }

    private JournalReplay replayJournal(List<Transaction> journalAsc) {
        List<SecurityEventDto> events = new ArrayList<>();
        List<SecurityMarkerDto> markers = new ArrayList<>();
        PositionState state = new PositionState(BigDecimal.ZERO, BigDecimal.ZERO);
        int buysCount = 0;
        int sellsCount = 0;
        for (int i = 0; i < journalAsc.size(); i++) {
            Transaction t = journalAsc.get(i);
            markers.add(toMarker(t));
            if (t.getType() == TransactionType.BUY) {
                state = applyBuy(state, t, events, i == 0);
                buysCount++;
            } else {
                SecurityEventKind kind = t.getType() == TransactionType.REDEMPTION
                        ? SecurityEventKind.REDEMPTION : SecurityEventKind.SELL;
                state = applySell(state, t, events, kind);
                sellsCount++;
            }
        }
        BigDecimal investedAllGross = sumByKinds(events, Set.of(SecurityEventKind.BUY), SecurityEventDto::getAmount);
        // A bond redemption reduces the position exactly like a sale, so its profit joins the
        // same "realized" total (plan point 2).
        BigDecimal realizedPnlTotal = sumByKinds(events, SELL_LIKE_KINDS, SecurityEventDto::getRealizedPnl);
        return new JournalReplay(events, markers, state.quantity(), investedAllGross, realizedPnlTotal, buysCount, sellsCount);
    }

    private BigDecimal sumByKinds(List<SecurityEventDto> events, Set<SecurityEventKind> kinds,
                                  Function<SecurityEventDto, BigDecimal> field) {
        return events.stream()
                .filter(e -> kinds.contains(e.getKind()))
                .map(field)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RM);
    }

    private SecurityMarkerDto toMarker(Transaction t) {
        return SecurityMarkerDto.builder()
                .date(toLocalDate(t.getExecutedAt()))
                .kind(t.getType())
                .quantity(t.getQuantity())
                .price(t.getPrice())
                .build();
    }

    // Mirrors TransactionService.recalculatePosition's BUY step: cost basis grows by the trade
    // amount, average is cost basis over quantity (same scale/rounding as Position.averagePrice).
    private PositionState applyBuy(PositionState state, Transaction t, List<SecurityEventDto> events, boolean first) {
        BigDecimal amount = t.getQuantity().multiply(t.getPrice()).setScale(SCALE, RM);
        BigDecimal quantity = state.quantity().add(t.getQuantity());
        BigDecimal costBasis = state.costBasis().add(amount);
        BigDecimal average = costBasis.divide(quantity, SCALE, RM);
        events.add(SecurityEventDto.builder()
                .kind(SecurityEventKind.BUY)
                .date(toLocalDate(t.getExecutedAt()))
                .createdAt(t.getCreatedAt())
                .transactionId(t.getId())
                .quantity(t.getQuantity())
                .price(t.getPrice())
                .amount(amount)
                .first(first)
                .positionAfter(new SecurityEventPositionDto(quantity, costBasis.setScale(SCALE, RM), average))
                .build());
        return new PositionState(quantity, costBasis);
    }

    // Mirrors TransactionService.recalculatePosition's SELL step: the average cost basis just
    // before this sell (8-decimal precision, same as recalculatePosition's avgCost) both prices
    // realizedPnl and the cost removed from the running basis. recalculatePosition rejects a sell
    // larger than the holding, so a drifted journal is the only way here; it must not turn the
    // page into a 500, hence a zero average when nothing is held and a negative remainder clamped
    // to zero and logged (same defensive guard as HoldingsOnDateService.quantityAt).
    private PositionState applySell(PositionState state, Transaction t, List<SecurityEventDto> events, SecurityEventKind kind) {
        BigDecimal averageBefore = state.quantity().signum() > 0
                ? state.costBasis().divide(state.quantity(), 8, RM)
                : BigDecimal.ZERO;
        BigDecimal amount = t.getQuantity().multiply(t.getPrice()).setScale(SCALE, RM);
        BigDecimal realizedPnl = t.getPrice().subtract(averageBefore).multiply(t.getQuantity()).setScale(SCALE, RM);
        BigDecimal quantity = state.quantity().subtract(t.getQuantity());
        if (quantity.signum() < 0) {
            log.warn("Отрицательный остаток при проигрывании журнала {}: {} — считаем нулём",
                    t.getSecurity().getTicker(), quantity);
            quantity = BigDecimal.ZERO;
        }
        BigDecimal costBasis = quantity.signum() == 0 ? BigDecimal.ZERO : state.costBasis().subtract(averageBefore.multiply(t.getQuantity()));
        BigDecimal average = quantity.signum() == 0 ? BigDecimal.ZERO : costBasis.divide(quantity, SCALE, RM);
        events.add(SecurityEventDto.builder()
                .kind(kind)
                .date(toLocalDate(t.getExecutedAt()))
                .createdAt(t.getCreatedAt())
                .transactionId(t.getId())
                .quantity(t.getQuantity())
                .price(t.getPrice())
                .amount(amount)
                .realizedPnl(realizedPnl)
                .positionAfter(new SecurityEventPositionDto(quantity, costBasis.setScale(SCALE, RM), average))
                .build());
        return new PositionState(quantity, costBasis);
    }

    private LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ---- dividends: PAID / UPCOMING classification, sums and the "next" candidate ----

    private record DividendBuild(List<SecurityEventDto> events, BigDecimal totalAll, BigDecimal total12m,
                                 SecurityNextDividendDto next) {
    }

    private DividendBuild buildDividendEvents(List<Dividend> dividends, Map<String, List<Transaction>> journalByTicker,
                                              String ticker, BigDecimal currentQuantity, LocalDate today) {
        List<SecurityEventDto> events = new ArrayList<>();
        BigDecimal totalAll = BigDecimal.ZERO;
        BigDecimal total12m = BigDecimal.ZERO;
        LocalDate windowStart = today.minusMonths(12);
        SecurityNextDividendDto next = null;
        LocalDate nextSortDate = null;

        for (Dividend dividend : dividends) {
            if (dividend.getStatus() == DividendStatus.CANCELLED || dividend.getAmountPerShare() == null) {
                continue;
            }
            LocalDate recordDate = dividend.getRecordDate();
            LocalDate paymentDate = dividend.getPaymentDate();
            if (DividendTiming.isReceived(recordDate, paymentDate, today)) {
                SecurityEventDto event = buildPaidEvent(dividend, journalByTicker, ticker);
                if (event == null) {
                    continue;
                }
                events.add(event);
                if (!RUB.equals(event.getCurrency())) {
                    continue;
                }
                totalAll = totalAll.add(event.getNetAmount());
                LocalDate receivedDate = DividendTiming.receivedDate(recordDate, paymentDate);
                if (!receivedDate.isBefore(windowStart) && receivedDate.isBefore(today)) {
                    total12m = total12m.add(event.getNetAmount());
                }
            } else if (DividendTiming.isUpcoming(recordDate, paymentDate, today)) {
                UpcomingBuild built = buildUpcoming(dividend, journalByTicker, ticker, currentQuantity, today);
                if (built == null) {
                    continue;
                }
                events.add(built.event());
                if (nextSortDate == null || built.sortDate().isBefore(nextSortDate)) {
                    nextSortDate = built.sortDate();
                    next = built.next();
                }
            }
        }
        return new DividendBuild(events, totalAll.setScale(SCALE, RM), total12m.setScale(SCALE, RM), next);
    }

    private SecurityEventDto buildPaidEvent(Dividend dividend, Map<String, List<Transaction>> journalByTicker, String ticker) {
        BigDecimal quantity = holdingsOnDateService.quantityAt(journalByTicker, ticker, dividend.getRecordDate());
        if (quantity.signum() == 0) {
            return null;
        }
        BigDecimal grossAmount = dividend.getAmountPerShare().multiply(quantity).setScale(SCALE, RM);
        BigDecimal netAmount = dividendTaxCalculator.netAmount(grossAmount, dividend.getCurrency());
        return SecurityEventDto.builder()
                .kind(SecurityEventKind.DIVIDEND_PAID)
                .date(DividendTiming.receivedDate(dividend.getRecordDate(), dividend.getPaymentDate()))
                .dividendId(dividend.getId())
                .amountPerShare(dividend.getAmountPerShare())
                .quantity(quantity)
                .netAmount(netAmount)
                .currency(dividend.getCurrency())
                .source(dividend.getSource())
                .payoutKind(dividend.getKind())
                .build();
    }

    private record UpcomingBuild(SecurityEventDto event, SecurityNextDividendDto next, LocalDate sortDate) {
    }

    private UpcomingBuild buildUpcoming(Dividend dividend, Map<String, List<Transaction>> journalByTicker,
                                        String ticker, BigDecimal currentQuantity, LocalDate today) {
        BigDecimal quantity = DividendTiming.isRecordDateAhead(dividend.getRecordDate(), today)
                ? currentQuantity
                : holdingsOnDateService.quantityAt(journalByTicker, ticker, dividend.getRecordDate());
        if (quantity.signum() == 0) {
            return null;
        }
        BigDecimal grossAmount = dividend.getAmountPerShare().multiply(quantity).setScale(SCALE, RM);
        BigDecimal netAmount = dividendTaxCalculator.netAmount(grossAmount, dividend.getCurrency());
        LocalDate sortDate = DividendTiming.upcomingEffectiveDate(dividend.getRecordDate(), dividend.getPaymentDate(), today);
        SecurityEventDto event = SecurityEventDto.builder()
                .kind(SecurityEventKind.DIVIDEND_UPCOMING)
                .date(sortDate)
                .dividendId(dividend.getId())
                .amountPerShare(dividend.getAmountPerShare())
                .quantity(quantity)
                .netAmount(netAmount)
                .currency(dividend.getCurrency())
                .paymentDate(dividend.getPaymentDate())
                .source(dividend.getSource())
                .payoutKind(dividend.getKind())
                .build();
        SecurityNextDividendDto next = SecurityNextDividendDto.builder()
                .recordDate(dividend.getRecordDate())
                .paymentDate(dividend.getPaymentDate())
                .amountPerShare(dividend.getAmountPerShare())
                .quantity(quantity)
                .netAmount(netAmount)
                .currency(dividend.getCurrency())
                .kind(dividend.getKind())
                .build();
        return new UpcomingBuild(event, next, sortDate);
    }

    private SecurityPageDividendsDto buildDividendsDto(DividendBuild dividendBuild, PositionResponseDto position) {
        BigDecimal totalCost = position != null ? position.getTotalCost() : null;
        return SecurityPageDividendsDto.builder()
                .total12m(dividendBuild.total12m())
                .totalAll(dividendBuild.totalAll())
                .yield12mPercent(portfolioGroupingService.percentOf(dividendBuild.total12m(), totalCost))
                .next(dividendBuild.next())
                .build();
    }

    // ---- position, price and result ----

    private record TickerPricing(PositionResponseDto position, SnapshotResult snapshot) {
    }

    // One batch snapshot call covers every open position plus this ticker (whose position may be
    // closed while the price block still needs a quote). computeTotals then enriches and weighs
    // the positions exactly as the /investments page does, so value, pnl and weightPercent here
    // match the portfolio card; its returned totals are not needed.
    private TickerPricing priceTicker(UUID userId, String ticker) {
        List<PositionResponseDto> positions = positionRepository.findByUserIdWithSecurity(userId).stream()
                .map(positionMapper::toDto)
                .toList();
        Set<String> tickers = new HashSet<>();
        positions.forEach(p -> tickers.add(p.getTicker()));
        tickers.add(ticker);
        Map<String, SnapshotResult> snapshots = marketDataService.getSnapshots(tickers);
        portfolioGroupingService.computeTotals(positions, snapshots);
        PositionResponseDto position = positions.stream()
                .filter(p -> ticker.equals(p.getTicker()))
                .findFirst()
                .orElse(null);
        return new TickerPricing(position, snapshots.get(ticker));
    }

    private SecurityPagePriceDto buildPriceDto(Security security, SnapshotResult snapshot) {
        if (snapshot == null || snapshot.lastPrice() == null) {
            return null;
        }
        BigDecimal current = bondPricing.quotedToRubles(security, snapshot.lastPrice());
        BigDecimal previousClose = bondPricing.quotedToRubles(security, snapshot.previousClose());
        BigDecimal dailyChangePercent = previousClose != null
                ? portfolioGroupingService.percentOf(current.subtract(previousClose), previousClose)
                : null;
        boolean nominalDefaulted = bondPricing.isQuotedAsPercentOfPar(security.getType()) && security.getNominal() == null;
        return SecurityPagePriceDto.builder()
                .current(current)
                .previousClose(previousClose)
                .dailyChangePercent(dailyChangePercent)
                .asOf(snapshot.fetchedAt())
                .stale(snapshot.stale())
                .accruedInterest(snapshot.accruedInterest())
                .nominalDefaulted(nominalDefaulted)
                .build();
    }

    private SecurityPageResultDto buildResult(PositionResponseDto position, JournalReplay replay, SecurityPageDividendsDto dividends) {
        BigDecimal pricePnl = position != null && position.getPnl() != null ? position.getPnl() : ZERO2;
        BigDecimal total = pricePnl.add(replay.realizedPnlTotal()).add(dividends.getTotalAll()).setScale(SCALE, RM);
        return SecurityPageResultDto.builder()
                .pricePnl(pricePnl)
                .realizedPnl(replay.realizedPnlTotal())
                .dividendsAll(dividends.getTotalAll())
                .total(total)
                .investedAll(replay.investedAllGross())
                .totalPercent(portfolioGroupingService.percentOf(total, replay.investedAllGross()))
                .build();
    }

    private SecurityPageSecurityDto toSecurityDto(Security security) {
        return SecurityPageSecurityDto.builder()
                .ticker(security.getTicker())
                .name(security.getName())
                .securityType(security.getType())
                .sector(security.getSector())
                .boardId(security.getBoardId())
                .historyStatus(security.getHistoryStatus())
                .build();
    }
}
