package pyc.lopatuxin.investment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.dto.request.ProjectionRequestDto;
import pyc.lopatuxin.investment.dto.response.ProjectionPointDto;
import pyc.lopatuxin.investment.dto.response.ProjectionResultDto;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;
import pyc.lopatuxin.investment.service.market.dto.SnapshotResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectionService {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int SCALE = 2;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private final PositionRepository positionRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final DividendRepository dividendRepository;
    private final MarketDataService marketDataService;

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
        BigDecimal weightedAnnualReturn = computeWeightedAnnualReturn(
                positions, currentValues, totalValue, req, pendingTickers);

        double wReturnDouble = weightedAnnualReturn.doubleValue();
        double monthlyReturnDouble = Math.pow(1.0 + wReturnDouble, 1.0 / 12.0) - 1.0;
        BigDecimal monthlyReturn = BigDecimal.valueOf(monthlyReturnDouble).setScale(8, RM);

        BigDecimal monthlyWithdrawalRate = req.getWithdrawalRatePerYear()
                .divide(BigDecimal.valueOf(12), MC);

        LocalDate now = LocalDate.now();
        List<ProjectionPointDto> series = simulateSeries(
                totalValue, monthlyReturn, monthlyWithdrawalRate, req, now);

        return ProjectionResultDto.builder()
                .startValue(totalValue.setScale(SCALE, RM))
                .portfolioWeightedAnnualReturn(weightedAnnualReturn.setScale(4, RM))
                .monthlyReturn(monthlyReturn)
                .series(series)
                .pendingHistoryTickers(pendingTickers)
                .build();
    }

    private BigDecimal computeCurrentValues(List<Position> positions,
                                            Map<String, SnapshotResult> snapshots,
                                            Map<String, BigDecimal> currentValues) {
        BigDecimal total = BigDecimal.ZERO;
        for (Position pos : positions) {
            String ticker = pos.getSecurity().getTicker();
            SnapshotResult snap = snapshots.get(ticker);
            BigDecimal price = (snap != null && snap.lastPrice() != null)
                    ? snap.lastPrice()
                    : pos.getAveragePrice();
            BigDecimal val = price.multiply(pos.getQuantity(), MC);
            currentValues.put(ticker, val);
            total = total.add(val);
        }
        return total;
    }

    private BigDecimal computeWeightedAnnualReturn(List<Position> positions,
                                                   Map<String, BigDecimal> currentValues,
                                                   BigDecimal totalValue,
                                                   ProjectionRequestDto req,
                                                   List<String> pendingTickers) {
        BigDecimal weightedReturn = BigDecimal.ZERO;
        for (Position pos : positions) {
            String ticker = pos.getSecurity().getTicker();
            BigDecimal annualReturn;

            if (req.getOverrides().containsKey(ticker)) {
                annualReturn = req.getOverrides().get(ticker);
            } else {
                annualReturn = computeAnnualReturn(ticker, pendingTickers);
            }

            BigDecimal weight = currentValues.get(ticker).divide(totalValue, MC);
            weightedReturn = weightedReturn.add(weight.multiply(annualReturn, MC));
        }
        return weightedReturn;
    }

    private BigDecimal computeAnnualReturn(String ticker, List<String> pendingTickers) {
        List<Dividend> paidDividends = dividendRepository.findBySecurity_Ticker(ticker)
                .stream()
                .filter(d -> d.getStatus() == DividendStatus.PAID)
                .toList();

        if (paidDividends.isEmpty()) {
            pendingTickers.add(ticker);
            return BigDecimal.ZERO;
        }

        Map<Integer, BigDecimal> yieldByYear = computeYieldByYear(ticker, paidDividends);

        if (yieldByYear.isEmpty()) {
            pendingTickers.add(ticker);
            return BigDecimal.ZERO;
        }

        BigDecimal sum = yieldByYear.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(yieldByYear.size()), MC);
    }

    private Map<Integer, BigDecimal> computeYieldByYear(String ticker, List<Dividend> dividends) {
        Map<Integer, BigDecimal> yieldByYear = new LinkedHashMap<>();
        for (Dividend div : dividends) {
            if (div.getPaymentDate() == null || div.getAmountPerShare() == null) {
                continue;
            }
            BigDecimal yieldI = computeDividendYield(ticker, div);
            if (yieldI == null) {
                continue;
            }
            int year = div.getPaymentDate().getYear();
            yieldByYear.merge(year, yieldI, BigDecimal::add);
        }
        return yieldByYear;
    }

    private BigDecimal computeDividendYield(String ticker, Dividend div) {
        return priceHistoryRepository
                .findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(ticker, div.getPaymentDate())
                .map(ph -> {
                    if (ph.getClose() == null || ph.getClose().compareTo(BigDecimal.ZERO) <= 0) {
                        return null;
                    }
                    return div.getAmountPerShare().divide(ph.getClose(), MC);
                })
                .orElse(null);
    }

    private List<ProjectionPointDto> simulateSeries(BigDecimal startValue,
                                                    BigDecimal monthlyReturn,
                                                    BigDecimal monthlyWithdrawalRate,
                                                    ProjectionRequestDto req,
                                                    LocalDate now) {
        BigDecimal value = startValue;
        List<ProjectionPointDto> series = new ArrayList<>();
        for (int m = 1; m <= req.getHorizonMonths(); m++) {
            value = value.multiply(BigDecimal.ONE.add(monthlyReturn), MC);
            value = value.add(req.getMonthlyDeposit());
            BigDecimal withdrawal = value.multiply(monthlyWithdrawalRate, MC).setScale(SCALE, RM);
            value = value.subtract(withdrawal);
            series.add(new ProjectionPointDto(
                    m,
                    now.plusMonths(m),
                    value.setScale(SCALE, RM),
                    req.getMonthlyDeposit().setScale(SCALE, RM),
                    withdrawal
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
                .build();
    }
}
