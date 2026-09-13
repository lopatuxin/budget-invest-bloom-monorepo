package pyc.lopatuxin.investment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.investment.dto.request.PortfolioSort;
import pyc.lopatuxin.investment.dto.response.PortfolioAllocationDto;
import pyc.lopatuxin.investment.dto.response.PortfolioGroupingResult;
import pyc.lopatuxin.investment.dto.response.PortfolioOverviewDto;
import pyc.lopatuxin.investment.dto.response.PortfolioPageResponseDto;
import pyc.lopatuxin.investment.dto.response.PortfolioSummaryDto;
import pyc.lopatuxin.investment.dto.response.PortfolioTotals;
import pyc.lopatuxin.investment.dto.response.PositionResponseDto;
import pyc.lopatuxin.investment.dto.response.TransactionResponseDto;
import pyc.lopatuxin.investment.dto.response.UpcomingDividendDto;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.mapper.PositionMapper;
import pyc.lopatuxin.investment.mapper.TransactionMapper;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.TransactionRepository;
import pyc.lopatuxin.investment.service.market.DividendSyncService;
import pyc.lopatuxin.investment.service.market.MarketDataService;
import pyc.lopatuxin.investment.dto.response.SnapshotResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private static final int RECENT_TRANSACTIONS_LIMIT = 5;
    private static final String SUMMED_CURRENCY = "RUB";

    private final PositionRepository positionRepository;
    private final PositionMapper positionMapper;
    private final MarketDataService marketDataService;
    private final DividendRepository dividendRepository;
    private final DividendSyncService dividendSyncService;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final PortfolioGroupingService portfolioGroupingService;
    private final HoldingsOnDateService holdingsOnDateService;
    private final DividendTaxCalculator dividendTaxCalculator;

    // Not wrapped in a transaction: loadPositionContext below hits the exchange over the
    // network (through MarketDataService.getSnapshots) before touching the database again,
    // so holding a Hikari connection for the whole method would tie up the pool for as long
    // as MOEX takes to answer. Each repository call here still runs in its own short
    // Spring Data transaction.
    public PortfolioPageResponseDto getPortfolioPage(UUID userId, PortfolioSort sort) {
        List<TransactionResponseDto> recentTransactions = transactionMapper.toDtoList(
                transactionRepository.findRecentByUserIdWithSecurity(userId, PageRequest.of(0, RECENT_TRANSACTIONS_LIMIT)));
        long transactionsTotal = transactionRepository.countByUserId(userId);

        PositionContext ctx = loadPositionContext(userId);
        if (ctx.positions().isEmpty() && ctx.dividendTickers().isEmpty()) {
            return emptyPage(recentTransactions, transactionsTotal);
        }

        // Computed once and threaded through every dividend calculation below instead of each
        // one calling LocalDate.now() on its own, so a midnight rollover mid-request cannot make
        // the 12-month window, the upcoming-dividends cutoff and the quantity rule disagree on
        // what day it is (plan point 3).
        LocalDate today = LocalDate.now();
        PortfolioGroupingResult grouping = portfolioGroupingService.group(ctx.basePositions(), ctx.snapshots(), sort);
        List<UpcomingDividendDto> recentDividends = buildRecentDividends(ctx.dividendTickers(), ctx.journalByTicker(), today);
        BigDecimal dividends12m = sumDividendAmounts(recentDividends);
        List<UpcomingDividendDto> upcomingDividends =
                buildUpcomingDividends(ctx.dividendTickers(), ctx.journalByTicker(), ctx.currentQuantityByTicker(), today);

        return PortfolioPageResponseDto.builder()
                .overview(buildOverview(grouping, dividends12m, ctx.snapshots()))
                .allocation(grouping.allocation())
                .groups(grouping.groups())
                .positions(grouping.positions())
                .upcomingDividends(upcomingDividends)
                .recentDividends(recentDividends)
                .recentTransactions(recentTransactions)
                .transactionsTotal(transactionsTotal)
                .build();
    }

    // Light path for PortfolioValuationAdapter: only the totals and upcoming dividends it
    // needs, skipping the recentTransactions/transactionsTotal queries and the groups/allocation
    // assembly the full /investments page requires.
    // See getPortfolioPage above: no outer transaction, for the same reason.
    public PortfolioSummaryDto getPortfolioSummary(UUID userId) {
        LocalDate today = LocalDate.now();
        PositionContext ctx = loadPositionContext(userId);
        if (ctx.positions().isEmpty() && ctx.dividendTickers().isEmpty()) {
            return new PortfolioSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, List.of(), today);
        }

        PortfolioTotals totals = portfolioGroupingService.computeTotals(ctx.basePositions(), ctx.snapshots());
        List<UpcomingDividendDto> recentDividends = buildRecentDividends(ctx.dividendTickers(), ctx.journalByTicker(), today);
        BigDecimal dividends12m = sumDividendAmounts(recentDividends);
        List<UpcomingDividendDto> upcomingDividends =
                buildUpcomingDividends(ctx.dividendTickers(), ctx.journalByTicker(), ctx.currentQuantityByTicker(), today);

        return new PortfolioSummaryDto(totals.totalValue(), totals.totalCost(), totals.totalPnl(),
                ctx.positions().size(), dividends12m, upcomingDividends, today);
    }

    // Shared prefix of getPortfolioPage and getPortfolioSummary: positions, market snapshots
    // (this is what actually fetches/upserts prices), the full trade journal and the DTOs both
    // callers enrich further, each in its own way.
    private PositionContext loadPositionContext(UUID userId) {
        List<Position> positions = positionRepository.findByUserIdWithSecurity(userId);
        Set<String> positionTickers = positions.stream()
                .map(p -> p.getSecurity().getTicker())
                .collect(Collectors.toSet());
        Map<String, SnapshotResult> snapshots = positionTickers.isEmpty()
                ? Map.of()
                : marketDataService.getSnapshots(positionTickers);
        // Merge function guards against two positions on the same ticker (uniqueness is only
        // a DB-level convention, not something this in-memory step can rely on) by summing
        // their quantities instead of throwing IllegalStateException.
        Map<String, BigDecimal> currentQuantityByTicker = positions.stream()
                .collect(Collectors.toMap(p -> p.getSecurity().getTicker(), Position::getQuantity, BigDecimal::add));
        List<PositionResponseDto> basePositions = positions.stream().map(positionMapper::toDto).toList();

        // The dividend ticker universe is the trade journal, not active positions (plan point 3):
        // a security sold in full still owes its past payouts to dividends12m/recentDividends.
        // Read once here and folded in memory by HoldingsOnDateService — never per dividend line.
        List<Transaction> journal = transactionRepository.findByUserIdWithSecurity(userId);
        Map<String, List<Transaction>> journalByTicker = holdingsOnDateService.groupSortedByTicker(journal);

        return new PositionContext(positions, snapshots, currentQuantityByTicker, basePositions,
                journalByTicker, journalByTicker.keySet());
    }

    // "Received" — paymentDate when known, recordDate otherwise (plan point 20). Currency is not
    // filtered here: a foreign-currency dividend is still shown in this list, only excluded from
    // the RUB sum in sumDividendAmounts below. Quantity is always the holding on the record date
    // (plan point 6), replayed from the journal, so a security since sold in full still counts.
    private List<UpcomingDividendDto> buildRecentDividends(Set<String> dividendTickers,
                                                            Map<String, List<Transaction>> journalByTicker, LocalDate today) {
        if (dividendTickers.isEmpty()) {
            return List.of();
        }
        List<Dividend> dividends = dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(
                dividendTickers, today.minusMonths(12), today);
        return toDividendDtos(dividends,
                d -> holdingsOnDateService.quantityAt(journalByTicker, d.getSecurity().getTicker(), d.getRecordDate()));
    }

    private record PositionContext(List<Position> positions,
                                   Map<String, SnapshotResult> snapshots,
                                   Map<String, BigDecimal> currentQuantityByTicker,
                                   List<PositionResponseDto> basePositions,
                                   Map<String, List<Transaction>> journalByTicker,
                                   Set<String> dividendTickers) {
    }

    private PortfolioPageResponseDto emptyPage(List<TransactionResponseDto> recentTransactions, long transactionsTotal) {
        return PortfolioPageResponseDto.builder()
                .overview(emptyOverview())
                .allocation(PortfolioAllocationDto.builder().byType(List.of()).bySector(List.of()).build())
                .groups(List.of())
                .positions(List.of())
                .upcomingDividends(List.of())
                .recentDividends(List.of())
                .recentTransactions(recentTransactions)
                .transactionsTotal(transactionsTotal)
                .build();
    }

    private PortfolioOverviewDto emptyOverview() {
        return PortfolioOverviewDto.builder()
                .totalValue(BigDecimal.ZERO)
                .totalCost(BigDecimal.ZERO)
                .totalPnl(BigDecimal.ZERO)
                .dailyPnl(BigDecimal.ZERO)
                .assetsCount(0)
                .sectorsCount(0)
                .dividends12m(BigDecimal.ZERO)
                .dividendTaxRatePercent(dividendTaxCalculator.taxRatePercent())
                .pricesStale(false)
                .unpricedCount(0)
                .dividendsSourceConfigured(dividendSyncService.isSourceConfigured())
                .build();
    }

    private PortfolioOverviewDto buildOverview(PortfolioGroupingResult grouping, BigDecimal dividends12m,
                                               Map<String, SnapshotResult> snapshots) {
        Instant pricesAsOf = snapshots.values().stream()
                .map(SnapshotResult::fetchedAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        boolean pricesStale = snapshots.values().stream().anyMatch(SnapshotResult::stale);
        BigDecimal dividendYieldPercent = portfolioGroupingService.percentOf(dividends12m, grouping.totalCost());

        return PortfolioOverviewDto.builder()
                .totalValue(grouping.totalValue())
                .totalCost(grouping.totalCost())
                .totalPnl(grouping.totalPnl())
                .totalPnlPercent(grouping.totalPnlPercent())
                .dailyPnl(grouping.dailyPnl())
                .dailyPnlPercent(grouping.dailyPnlPercent())
                .assetsCount(grouping.assetsCount())
                .sectorsCount(grouping.sectorsCount())
                .dividends12m(dividends12m)
                .dividendYieldPercent(dividendYieldPercent)
                .dividendTaxRatePercent(dividendTaxCalculator.taxRatePercent())
                .pricesAsOf(pricesAsOf)
                .pricesStale(pricesStale)
                .unpricedCount(grouping.unpricedCount())
                .dividendsSourceConfigured(dividendSyncService.isSourceConfigured())
                .build();
    }

    private List<UpcomingDividendDto> buildUpcomingDividends(Set<String> dividendTickers,
                                                              Map<String, List<Transaction>> journalByTicker,
                                                              Map<String, BigDecimal> currentQuantityByTicker,
                                                              LocalDate today) {
        if (dividendTickers.isEmpty()) {
            return List.of();
        }
        List<Dividend> dividends = dividendRepository.findUpcomingByTickersWithSecurity(dividendTickers, today);
        List<UpcomingDividendDto> result = toDividendDtos(
                dividends, d -> upcomingQuantity(d, journalByTicker, currentQuantityByTicker, today));
        result.sort(Comparator.comparing(d -> d.effectiveDate(today)));
        return result;
    }

    // Record date still ahead (or today, plan's "cutoff today" edge case) — nobody knows the
    // future holding, so today's is used; record date already passed and only payment is
    // pending — the holding is already fixed, same rule as recentDividends (plan point 7).
    private BigDecimal upcomingQuantity(Dividend dividend, Map<String, List<Transaction>> journalByTicker,
                                        Map<String, BigDecimal> currentQuantityByTicker, LocalDate today) {
        String ticker = dividend.getSecurity().getTicker();
        if (DividendTiming.isRecordDateAhead(dividend.getRecordDate(), today)) {
            return currentQuantityByTicker.getOrDefault(ticker, BigDecimal.ZERO);
        }
        return holdingsOnDateService.quantityAt(journalByTicker, ticker, dividend.getRecordDate());
    }

    // Shared by upcoming and recent (paid) dividends: same shape, same per-line rules — only the
    // quantity resolver differs between the two callers (plan points 6-7). totalAmount is the
    // sum after tax (plan point 15); amountPerShare stays the declared per-share amount as is.
    private List<UpcomingDividendDto> toDividendDtos(List<Dividend> dividends, Function<Dividend, BigDecimal> quantityResolver) {
        List<UpcomingDividendDto> result = new ArrayList<>();
        for (Dividend d : dividends) {
            if (d.getAmountPerShare() == null) {
                continue;
            }
            BigDecimal qty = quantityResolver.apply(d);
            if (qty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal grossAmount = d.getAmountPerShare().multiply(qty).setScale(2, RoundingMode.HALF_UP);
            BigDecimal netAmount = dividendTaxCalculator.netAmount(grossAmount, d.getCurrency());
            result.add(UpcomingDividendDto.builder()
                    .ticker(d.getSecurity().getTicker())
                    .securityName(d.getSecurity().getName())
                    .recordDate(d.getRecordDate())
                    .paymentDate(d.getPaymentDate())
                    .amountPerShare(d.getAmountPerShare())
                    .quantity(qty)
                    .totalAmount(netAmount)
                    .currency(d.getCurrency())
                    .build());
        }
        return result;
    }

    // A foreign-currency dividend is still shown "as is" in recentDividends (filtered at the
    // query only by receipt-date window and status), just not counted toward the RUB total.
    private BigDecimal sumDividendAmounts(List<UpcomingDividendDto> dividends) {
        return dividends.stream()
                .filter(d -> SUMMED_CURRENCY.equals(d.getCurrency()))
                .map(UpcomingDividendDto::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
