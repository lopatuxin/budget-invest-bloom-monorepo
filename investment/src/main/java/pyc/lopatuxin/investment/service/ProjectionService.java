package pyc.lopatuxin.investment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.config.DividendTaxProperties;
import pyc.lopatuxin.investment.dto.request.ProjectionRequestDto;
import pyc.lopatuxin.investment.dto.response.ProjectionBreakdownItemDto;
import pyc.lopatuxin.investment.dto.response.ProjectionPayoutKind;
import pyc.lopatuxin.investment.dto.response.ProjectionPointDto;
import pyc.lopatuxin.investment.dto.response.ProjectionResultDto;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.DividendSource;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.PayoutKind;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;
import pyc.lopatuxin.investment.dto.response.SnapshotResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(value = "investmentTransactionManager", readOnly = true)
public class ProjectionService {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int SCALE = 2;
    private static final RoundingMode RM = RoundingMode.HALF_UP;
    private static final int PERCENT_SCALE = 1;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String TAXABLE_CURRENCY = "RUB";

    // The payout-yield average is taken over the last five full calendar years, the current year
    // excluded (plan point 11) — a payout declared but not yet paid this year cannot inflate the
    // average, and a company that stopped paying gradually fades toward zero as old years roll
    // out of the window.
    private static final int PAYOUT_WINDOW_YEARS = 5;
    // Price growth looks back at most ten years (plan point 12).
    private static final int PRICE_GROWTH_WINDOW_YEARS = 10;
    // A day-to-day close ratio outside [1/3, 3] is treated as a split/reverse split, not real
    // price movement — the growth window restarts the day after the most recent such jump.
    private static final BigDecimal SPLIT_RATIO_UP = BigDecimal.valueOf(3);
    private static final BigDecimal SPLIT_RATIO_DOWN = BigDecimal.ONE.divide(SPLIT_RATIO_UP, MC);
    private static final int MIN_CAGR_WINDOW_DAYS = 30;
    // A payout-window year counts only once price history reaches back to at least its first
    // trading week (plan point 6) — MOEX/most exchanges are closed the first few days of January,
    // so a quote landing anywhere in the first calendar week is "the security had a price all
    // year", not "bought partway through".
    private static final int FIRST_TRADING_WEEK_DAY_OF_YEAR = 7;
    // Same-payout duplicates from the two sync sources land up to ~2 weeks apart (ex-dividend
    // date vs fixation date can differ that much) but never further.
    private static final int DUPLICATE_PAYOUT_WINDOW_DAYS = 20;

    private final PositionRepository positionRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final DividendRepository dividendRepository;
    private final MarketDataService marketDataService;
    private final BondPricing bondPricing;
    private final DividendTaxProperties dividendTaxProperties;

    public ProjectionResultDto project(UUID userId, ProjectionRequestDto req) {
        List<Position> positions = positionRepository.findByUserIdWithSecurity(userId);
        if (positions.isEmpty()) {
            return emptyResult();
        }

        List<String> tickers = positions.stream()
                .map(p -> p.getSecurity().getTicker())
                .toList();

        Map<String, SnapshotResult> snapshots = marketDataService.getSnapshots(tickers);

        Map<String, BigDecimal> currentValues = new LinkedHashMap<>();
        BigDecimal totalValue = computeCurrentValues(positions, snapshots, currentValues);

        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            totalValue = BigDecimal.ONE;
        }

        List<String> pendingTickers = new ArrayList<>();
        List<ProjectionBreakdownItemDto> breakdown = new ArrayList<>();
        WeightedReturns weighted = computeWeightedAnnualReturn(
                positions, currentValues, totalValue, req, pendingTickers, breakdown);
        breakdown.sort(Comparator.comparing(ProjectionBreakdownItemDto::getWeightPercent).reversed());

        BigDecimal weightedAnnualReturn = weighted.total();
        // priceGrowthPercent is rounded independently; payoutYieldPercent is derived by
        // subtraction from the already-rounded total rather than rounded on its own, so the two
        // always sum to exactly portfolioWeightedAnnualReturn * 100 (plan point 13) regardless of
        // independent rounding drift.
        BigDecimal totalPercent = weightedAnnualReturn.multiply(HUNDRED).setScale(PERCENT_SCALE, RM);
        BigDecimal priceGrowthPercent = weighted.priceGrowth().multiply(HUNDRED).setScale(PERCENT_SCALE, RM);
        BigDecimal payoutYieldPercent = totalPercent.subtract(priceGrowthPercent);

        double wReturnDouble = weightedAnnualReturn.doubleValue();
        double monthlyReturnDouble = Math.pow(1.0 + wReturnDouble, 1.0 / 12.0) - 1.0;
        BigDecimal monthlyReturn = BigDecimal.valueOf(monthlyReturnDouble).setScale(8, RM);

        BigDecimal monthlyWithdrawalRate = req.getWithdrawalRatePerYear()
                .divide(BigDecimal.valueOf(12), MC);

        LocalDate now = LocalDate.now();
        List<ProjectionPointDto> series = simulateSeries(
                totalValue, monthlyReturn, monthlyWithdrawalRate, req, now);

        // series is never empty here: the only empty-portfolio case (no positions) already
        // returned via emptyResult() above, and horizonMonths is validated >= 1.
        ProjectionPointDto lastPoint = series.get(series.size() - 1);
        BigDecimal contributedTotal = lastPoint.getContributed();
        BigDecimal earned = lastPoint.getValue().subtract(contributedTotal);

        return ProjectionResultDto.builder()
                .startValue(totalValue.setScale(SCALE, RM))
                .portfolioWeightedAnnualReturn(weightedAnnualReturn.setScale(4, RM))
                .monthlyReturn(monthlyReturn)
                .series(series)
                .pendingHistoryTickers(pendingTickers)
                .contributedTotal(contributedTotal)
                .earned(earned)
                .priceGrowthPercent(priceGrowthPercent)
                .payoutYieldPercent(payoutYieldPercent)
                .breakdown(breakdown)
                .build();
    }

    // Current value of a position (plan point 3): quantity × (ruble price + accrued coupon
    // interest for a bond/OFZ) when a live snapshot is known, otherwise the trade-journal average
    // price (already rubles, needs no conversion).
    private BigDecimal computeCurrentValues(List<Position> positions,
                                            Map<String, SnapshotResult> snapshots,
                                            Map<String, BigDecimal> currentValues) {
        BigDecimal total = BigDecimal.ZERO;
        for (Position pos : positions) {
            Security security = pos.getSecurity();
            String ticker = security.getTicker();
            SnapshotResult snap = snapshots.get(ticker);
            BigDecimal val;
            if (snap != null && snap.lastPrice() != null) {
                val = bondPricing.rubleValue(security, snap.lastPrice(), snap.accruedInterest(), pos.getQuantity());
            } else {
                val = pos.getAveragePrice().multiply(pos.getQuantity(), MC);
            }
            currentValues.put(ticker, val);
            total = total.add(val);
        }
        return total;
    }

    // priceGrowth/payoutYield are the same unrounded fractions computeWeightedAnnualReturn sums
    // into `total` — kept apart here only so project() can round each portfolio-level percentage
    // once, from the same numbers, instead of re-deriving them from the rounded breakdown rows.
    private record WeightedReturns(BigDecimal total, BigDecimal priceGrowth) {
    }

    private WeightedReturns computeWeightedAnnualReturn(List<Position> positions,
                                                         Map<String, BigDecimal> currentValues,
                                                         BigDecimal totalValue,
                                                         ProjectionRequestDto req,
                                                         List<String> pendingTickers,
                                                         List<ProjectionBreakdownItemDto> breakdown) {
        LocalDate today = LocalDate.now();
        BigDecimal weightedReturn = BigDecimal.ZERO;
        BigDecimal weightedPriceGrowth = BigDecimal.ZERO;
        for (Position pos : positions) {
            Security security = pos.getSecurity();
            String ticker = security.getTicker();
            BigDecimal weight = currentValues.get(ticker).divide(totalValue, MC);
            BigDecimal weightPercent = weight.multiply(HUNDRED).setScale(PERCENT_SCALE, RM);

            BigDecimal annualReturn;
            BigDecimal priceCagr;
            ProjectionBreakdownItemDto.ProjectionBreakdownItemDtoBuilder item = ProjectionBreakdownItemDto.builder()
                    .ticker(ticker)
                    .securityName(security.getName())
                    .securityType(security.getType())
                    .weightPercent(weightPercent);

            if (req.getOverrides().containsKey(ticker)) {
                annualReturn = req.getOverrides().get(ticker);
                priceCagr = annualReturn;
                item.priceGrowthPercent(toPercent(priceCagr))
                        .payoutYieldPercent(BigDecimal.ZERO.setScale(PERCENT_SCALE, RM))
                        .payoutKind(ProjectionPayoutKind.NONE)
                        .yearsCounted(0)
                        .lastPayoutYear(null)
                        .historyPending(false);
            } else {
                SecurityReturn sr = computeSecurityReturn(security, today, pendingTickers);
                priceCagr = sr.priceCagr();
                annualReturn = priceCagr.add(sr.payoutYield());
                item.priceGrowthPercent(toPercent(priceCagr))
                        .payoutYieldPercent(toPercent(sr.payoutYield()))
                        .payoutKind(sr.payoutKind())
                        .yearsCounted(sr.yearsCounted())
                        .lastPayoutYear(sr.lastPayoutYear())
                        .historyPending(sr.historyPending());
            }

            weightedReturn = weightedReturn.add(weight.multiply(annualReturn, MC));
            weightedPriceGrowth = weightedPriceGrowth.add(weight.multiply(priceCagr, MC));
            breakdown.add(item.build());
        }
        return new WeightedReturns(weightedReturn, weightedPriceGrowth);
    }

    private BigDecimal toPercent(BigDecimal fraction) {
        return fraction.multiply(HUNDRED).setScale(PERCENT_SCALE, RM);
    }

    // One security's own price growth and payout yield, independent of its portfolio weight
    // (plan points 10-13, 17-19).
    private record SecurityReturn(BigDecimal priceCagr, BigDecimal payoutYield, ProjectionPayoutKind payoutKind,
                                  int yearsCounted, Integer lastPayoutYear, boolean historyPending) {
    }

    private SecurityReturn computeSecurityReturn(Security security, LocalDate today, List<String> pendingTickers) {
        String ticker = security.getTicker();
        List<PriceHistory> fullHistory = priceHistoryRepository.findByTickerOrderByTradeDateAsc(ticker);
        Optional<LocalDate> splitBoundary = findLastSplitBoundary(fullHistory);
        BigDecimal priceCagr = computePriceCagr(security, fullHistory, splitBoundary, today);
        boolean hasPriceData = priceCagr.compareTo(BigDecimal.ZERO) != 0;

        List<Dividend> allPayouts = dividendRepository.findBySecurity_Ticker(ticker);
        List<Dividend> paidPayouts = dedupeNearbyPayouts(allPayouts.stream()
                .filter(d -> d.getStatus() == DividendStatus.PAID)
                .toList());

        Set<Integer> coveredYears = resolveCoveredYears(fullHistory, splitBoundary, today);
        PayoutYieldResult payoutResult = computeWindowedPayoutYield(security, paidPayouts, coveredYears, splitBoundary, today);

        Integer lastPayoutYear = paidPayouts.stream()
                .map(this::effectiveYear)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        ProjectionPayoutKind payoutKind = resolvePayoutKind(allPayouts);

        boolean historyPending = !hasPriceData && !payoutResult.hasData();
        // A bond/OFZ whose coupon sync has not run yet (or found nothing at all) is "pending", not
        // "genuinely pays nothing" — even once its price history is otherwise complete (plan
        // point 18); a security that stopped paying after a real sync stays a real zero.
        if (bondPricing.isQuotedAsPercentOfPar(security.getType()) && !payoutResult.hasData()
                && (security.getDividendsSyncedAt() == null || allPayouts.isEmpty())) {
            historyPending = true;
            payoutKind = ProjectionPayoutKind.NONE;
        }
        if (historyPending) {
            pendingTickers.add(ticker);
        }

        return new SecurityReturn(priceCagr, payoutResult.yield(), payoutKind, payoutResult.yearsCounted(), lastPayoutYear, historyPending);
    }

    private ProjectionPayoutKind resolvePayoutKind(List<Dividend> allPayouts) {
        if (allPayouts.isEmpty()) {
            return ProjectionPayoutKind.NONE;
        }
        return allPayouts.get(0).getKind() == PayoutKind.COUPON ? ProjectionPayoutKind.COUPON : ProjectionPayoutKind.DIVIDEND;
    }

    private LocalDate effectiveDate(Dividend d) {
        return d.getPaymentDate() != null ? d.getPaymentDate() : d.getRecordDate();
    }

    private Integer effectiveYear(Dividend d) {
        LocalDate effectiveDate = effectiveDate(d);
        return effectiveDate != null ? effectiveDate.getYear() : null;
    }

    // The same real-world payout can reach the DB twice under different record dates — MOEX's
    // ex-dividend date and Т-Инвестиции's fixation date for one payment typically differ by under
    // three weeks — and DB uniqueness is per (ticker, record_date), so both rows persist. A MOEX
    // row with a TINVEST row within 20 days is that duplicate and is skipped here without touching
    // the stored rows. Rows of one source are never collapsed: MOEX alone has distinct payouts
    // 9-12 days apart, and a MANUAL row is the user's own entry.
    private List<Dividend> dedupeNearbyPayouts(List<Dividend> payouts) {
        List<LocalDate> tinvestRecordDates = payouts.stream()
                .filter(d -> d.getSource() == DividendSource.TINVEST && d.getRecordDate() != null)
                .map(Dividend::getRecordDate)
                .toList();
        return payouts.stream()
                .filter(d -> d.getRecordDate() != null)
                .filter(d -> d.getSource() != DividendSource.MOEX
                        || tinvestRecordDates.stream().noneMatch(date ->
                                Math.abs(ChronoUnit.DAYS.between(date, d.getRecordDate())) <= DUPLICATE_PAYOUT_WINDOW_DAYS))
                .sorted(Comparator.comparing(Dividend::getRecordDate))
                .toList();
    }

    // Which of the last five full calendar years the payout window can actually average over
    // (plan points 6, 11): a year counts only once price history reaches back to at least its
    // first trading week — a security bought partway through a year does not get that year's few
    // weeks of data padded into a "full year". A split/reverse split (plan point 1) pushes the
    // effective start forward the same way a late purchase does: a pre-split price cannot back a
    // payout-year's yield ratio, so no window year before (or only partly after) the split counts.
    private Set<Integer> resolveCoveredYears(List<PriceHistory> fullHistory, Optional<LocalDate> splitBoundary, LocalDate today) {
        if (fullHistory.isEmpty()) {
            return Set.of();
        }
        LocalDate effectiveStart = fullHistory.get(0).getTradeDate();
        if (splitBoundary.isPresent() && splitBoundary.get().isAfter(effectiveStart)) {
            effectiveStart = splitBoundary.get();
        }
        LocalDate start = effectiveStart;
        return IntStream.rangeClosed(today.getYear() - PAYOUT_WINDOW_YEARS, today.getYear() - 1)
                .filter(year -> isYearCovered(start, year))
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isYearCovered(LocalDate effectiveStart, int year) {
        if (year > effectiveStart.getYear()) {
            return true;
        }
        if (year < effectiveStart.getYear()) {
            return false;
        }
        return effectiveStart.getDayOfYear() <= FIRST_TRADING_WEEK_DAY_OF_YEAR;
    }

    // Average-annual-return input for price growth (plan point 12): at most the last ten years
    // of trade history, restarting the day after the most recent split/reverse-split-sized jump
    // (a day-to-day close ratio outside [1/3, 3]) so a historical corporate action does not read
    // as real price movement.
    private BigDecimal computePriceCagr(Security security, List<PriceHistory> fullHistory,
                                        Optional<LocalDate> splitBoundary, LocalDate today) {
        if (fullHistory.size() < 2) {
            return BigDecimal.ZERO;
        }
        LocalDate windowStart = today.minusYears(PRICE_GROWTH_WINDOW_YEARS);
        List<PriceHistory> windowed = fullHistory.stream()
                .filter(ph -> !ph.getTradeDate().isBefore(windowStart))
                .filter(ph -> splitBoundary.isEmpty() || !ph.getTradeDate().isBefore(splitBoundary.get()))
                .toList();
        if (windowed.size() < 2) {
            return BigDecimal.ZERO;
        }

        LocalDate firstDate = windowed.get(0).getTradeDate();
        LocalDate lastDate = windowed.get(windowed.size() - 1).getTradeDate();
        if (ChronoUnit.DAYS.between(firstDate, lastDate) < MIN_CAGR_WINDOW_DAYS) {
            return BigDecimal.ZERO;
        }

        BigDecimal priceStart = bondPricing.quotedToRubles(security, windowed.get(0).getClose());
        BigDecimal priceEnd = bondPricing.quotedToRubles(security, windowed.get(windowed.size() - 1).getClose());
        if (priceStart == null || priceStart.compareTo(BigDecimal.ZERO) <= 0 || priceEnd == null) {
            return BigDecimal.ZERO;
        }

        double years = ChronoUnit.DAYS.between(firstDate, lastDate) / 365.25;
        double cagr = Math.pow(priceEnd.doubleValue() / priceStart.doubleValue(), 1.0 / years) - 1.0;
        return BigDecimal.valueOf(cagr);
    }

    // The one split/reverse-split detector shared by price growth and payout yield (plan point
    // 1, not a copy per security): a day-to-day close ratio outside [1/3, 3] anywhere in the
    // security's full price history marks the day of the most recent such jump. It runs on the
    // raw exchange quote (not the ruble-converted price) — a BOND/OFZ's quoted-to-rubles
    // conversion is a constant multiplier per security, so it cannot change which ratio crosses
    // the threshold. Only the most recent qualifying jump matters: an older split is irrelevant
    // once a newer one has already invalidated everything before it.
    private Optional<LocalDate> findLastSplitBoundary(List<PriceHistory> sortedHistory) {
        LocalDate boundary = null;
        for (int i = 1; i < sortedHistory.size(); i++) {
            BigDecimal prev = sortedHistory.get(i - 1).getClose();
            BigDecimal curr = sortedHistory.get(i).getClose();
            if (prev == null || curr == null || prev.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal ratio = curr.divide(prev, MC);
            if (ratio.compareTo(SPLIT_RATIO_UP) > 0 || ratio.compareTo(SPLIT_RATIO_DOWN) < 0) {
                boundary = sortedHistory.get(i).getTradeDate();
            }
        }
        return Optional.ofNullable(boundary);
    }

    // priceCagr/payoutYield are two independent unrounded fractions plus the divisor actually
    // used for the sum — bundled so computeSecurityReturn can report yearsCounted without
    // recomputing it (plan point 6: "yearsCounted в разбивке = этот делитель").
    private record PayoutYieldResult(BigDecimal yield, int yearsCounted, boolean hasData) {
    }

    // Sums each paid payout's after-tax yield ("выплата / цена бумаги в день выплаты", plan point
    // 11) over the calendar years price history actually covers (plan point 6) — a payout outside
    // that set (too old, this still-open year, or a year price history does not yet reach back
    // to) never enters the sum, same effect as "years without a payout count as zero" once the
    // sum is divided by the covered-years count. A payout dated before the last detected
    // split/reverse split is skipped outright regardless of year (plan point 1): its per-share
    // amount was recalculated onto a post-split share count, so it cannot be divided by a
    // pre-split price. When no full covered year exists yet, falls back to whatever was paid in
    // the trailing twelve months with divisor 1, rather than pretending a security bought this
    // year already has empty years behind it.
    private PayoutYieldResult computeWindowedPayoutYield(Security security, List<Dividend> paidPayouts,
                                                         Set<Integer> coveredYears, Optional<LocalDate> splitBoundary,
                                                         LocalDate today) {
        List<Dividend> eligible = paidPayouts.stream()
                .filter(d -> splitBoundary.isEmpty() || d.getRecordDate() == null
                        || !d.getRecordDate().isBefore(splitBoundary.get()))
                .toList();
        if (!coveredYears.isEmpty()) {
            Map<Integer, BigDecimal> yieldByYear = computeYieldByYear(security, eligible, coveredYears);
            BigDecimal sum = yieldByYear.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            return new PayoutYieldResult(sum.divide(BigDecimal.valueOf(coveredYears.size()), MC),
                    coveredYears.size(), !yieldByYear.isEmpty());
        }
        return computeTrailingTwelveMonthsYield(security, eligible, today);
    }

    private PayoutYieldResult computeTrailingTwelveMonthsYield(Security security, List<Dividend> eligible, LocalDate today) {
        LocalDate trailingStart = today.minusMonths(12);
        BigDecimal sum = BigDecimal.ZERO;
        boolean hasData = false;
        for (Dividend payout : eligible) {
            LocalDate effectiveDate = effectiveDate(payout);
            if (payout.getAmountPerShare() == null || effectiveDate == null
                    || effectiveDate.isBefore(trailingStart) || effectiveDate.isAfter(today)) {
                continue;
            }
            BigDecimal yieldRatio = taxedYieldRatio(security, payout, effectiveDate);
            if (yieldRatio == null) {
                continue;
            }
            sum = sum.add(yieldRatio);
            hasData = true;
        }
        return new PayoutYieldResult(sum, 1, hasData);
    }

    private Map<Integer, BigDecimal> computeYieldByYear(Security security, List<Dividend> payouts, Set<Integer> coveredYears) {
        Map<Integer, BigDecimal> yieldByYear = new LinkedHashMap<>();
        for (Dividend payout : payouts) {
            LocalDate effectiveDate = effectiveDate(payout);
            if (payout.getAmountPerShare() == null || effectiveDate == null || !coveredYears.contains(effectiveDate.getYear())) {
                continue;
            }
            BigDecimal yieldRatio = taxedYieldRatio(security, payout, effectiveDate);
            if (yieldRatio == null) {
                continue;
            }
            yieldByYear.merge(effectiveDate.getYear(), yieldRatio, BigDecimal::add);
        }
        return yieldByYear;
    }

    private BigDecimal taxedYieldRatio(Security security, Dividend payout, LocalDate effectiveDate) {
        BigDecimal yieldRatio = computePayoutYield(security, payout.getAmountPerShare(), effectiveDate);
        if (yieldRatio == null) {
            return null;
        }
        if (TAXABLE_CURRENCY.equals(payout.getCurrency())) {
            return yieldRatio.multiply(BigDecimal.ONE.subtract(dividendTaxProperties.getRate()), MC);
        }
        return yieldRatio;
    }

    // The exchange quote on the payout date, converted through BondPricing before dividing — a
    // bond's/OFZ's PriceHistory.close is percent-of-par, not rubles (plan point 2).
    private BigDecimal computePayoutYield(Security security, BigDecimal amountPerShare, LocalDate effectiveDate) {
        return priceHistoryRepository
                .findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(security.getTicker(), effectiveDate)
                .map(ph -> {
                    BigDecimal closeRub = bondPricing.quotedToRubles(security, ph.getClose());
                    if (closeRub == null || closeRub.compareTo(BigDecimal.ZERO) <= 0) {
                        return null;
                    }
                    return amountPerShare.divide(closeRub, MC);
                })
                .orElse(null);
    }

    private List<ProjectionPointDto> simulateSeries(BigDecimal startValue,
                                                    BigDecimal monthlyReturn,
                                                    BigDecimal monthlyWithdrawalRate,
                                                    ProjectionRequestDto req,
                                                    LocalDate now) {
        BigDecimal value = startValue;
        // Principal tracked separately from value: it only moves by deposits and withdrawals,
        // never by the simulated return, so contributedTotal/earned can tell "money I put in"
        // apart from "growth" at any point on the series (plan item 3 of the plan's API section).
        BigDecimal contributed = startValue;
        List<ProjectionPointDto> series = new ArrayList<>();
        for (int m = 1; m <= req.getHorizonMonths(); m++) {
            value = value.multiply(BigDecimal.ONE.add(monthlyReturn), MC);
            value = value.add(req.getMonthlyDeposit());
            BigDecimal withdrawal = value.multiply(monthlyWithdrawalRate, MC).setScale(SCALE, RM);
            value = value.subtract(withdrawal);
            contributed = contributed.add(req.getMonthlyDeposit()).subtract(withdrawal);
            series.add(new ProjectionPointDto(
                    m,
                    now.plusMonths(m),
                    value.setScale(SCALE, RM),
                    req.getMonthlyDeposit().setScale(SCALE, RM),
                    withdrawal,
                    contributed.setScale(SCALE, RM)
            ));
        }
        return series;
    }

    private ProjectionResultDto emptyResult() {
        return ProjectionResultDto.builder()
                .startValue(BigDecimal.ZERO)
                .portfolioWeightedAnnualReturn(BigDecimal.ZERO)
                .monthlyReturn(BigDecimal.ZERO)
                .series(List.of())
                .pendingHistoryTickers(List.of())
                .contributedTotal(BigDecimal.ZERO)
                .earned(BigDecimal.ZERO)
                .priceGrowthPercent(BigDecimal.ZERO.setScale(PERCENT_SCALE, RM))
                .payoutYieldPercent(BigDecimal.ZERO.setScale(PERCENT_SCALE, RM))
                .breakdown(List.of())
                .build();
    }
}
