package pyc.lopatuxin.investment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.dto.request.ProjectionRequestDto;
import pyc.lopatuxin.investment.dto.response.ProjectionPointDto;
import pyc.lopatuxin.investment.dto.response.ProjectionResultDto;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;
import pyc.lopatuxin.investment.service.market.dto.SnapshotResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        LocalDate now = LocalDate.now();
        LocalDate lookbackFrom = now.minusYears(req.getLookbackYears());
        List<String> pendingTickers = new ArrayList<>();
        BigDecimal weightedAnnualReturn = computeWeightedAnnualReturn(
                positions, currentValues, totalValue, snapshots, req, lookbackFrom, now, pendingTickers);

        double wReturnDouble = weightedAnnualReturn.doubleValue();
        double monthlyReturnDouble = Math.pow(1.0 + wReturnDouble, 1.0 / 12.0) - 1.0;
        BigDecimal monthlyReturn = BigDecimal.valueOf(monthlyReturnDouble).setScale(8, RM);

        BigDecimal monthlyWithdrawalRate = req.getWithdrawalRatePerYear()
                .divide(BigDecimal.valueOf(12), MC);

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
                                                   Map<String, SnapshotResult> snapshots,
                                                   ProjectionRequestDto req,
                                                   LocalDate lookbackFrom,
                                                   LocalDate now,
                                                   List<String> pendingTickers) {
        BigDecimal weightedReturn = BigDecimal.ZERO;
        for (Position pos : positions) {
            String ticker = pos.getSecurity().getTicker();
            BigDecimal annualReturn;

            if (req.getOverrides().containsKey(ticker)) {
                annualReturn = req.getOverrides().get(ticker);
            } else {
                annualReturn = computeHistoricalAnnualReturn(ticker, lookbackFrom, now, pendingTickers);
            }

            annualReturn = annualReturn.add(computeDividendYield(ticker, currentValues.get(ticker), pos.getQuantity(), now));

            BigDecimal weight = currentValues.get(ticker).divide(totalValue, MC);
            weightedReturn = weightedReturn.add(weight.multiply(annualReturn, MC));
        }
        return weightedReturn;
    }

    private BigDecimal computeDividendYield(String ticker, BigDecimal positionValue,
                                            BigDecimal quantity, LocalDate now) {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        BigDecimal currentPrice = positionValue.divide(quantity, MC);
        if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        BigDecimal dividendSum = dividendRepository
                .findBySecurity_TickerAndPaymentDateAfter(ticker, now.minusYears(1))
                .stream()
                .map(d -> d.getAmountPerShare() != null ? d.getAmountPerShare() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return dividendSum.divide(currentPrice, MC);
    }

    private BigDecimal computeHistoricalAnnualReturn(String ticker, LocalDate from, LocalDate to,
                                                     List<String> pendingTickers) {
        List<PriceHistory> history = priceHistoryRepository
                .findByTickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, from, to);

        if (history.size() < 2) {
            pendingTickers.add(ticker);
            return BigDecimal.ZERO;
        }

        BigDecimal startClose = history.get(0).getClose();
        BigDecimal endClose = history.get(history.size() - 1).getClose();

        if (startClose == null || startClose.compareTo(BigDecimal.ZERO) == 0 || endClose == null) {
            pendingTickers.add(ticker);
            return BigDecimal.ZERO;
        }

        double ratio = endClose.divide(startClose, MC).doubleValue();
        double actualYears = (double) history.get(0).getTradeDate()
                .until(history.get(history.size() - 1).getTradeDate(), ChronoUnit.DAYS) / 365.25;

        if (actualYears <= 0) {
            pendingTickers.add(ticker);
            return BigDecimal.ZERO;
        }

        double cagr = Math.pow(ratio, 1.0 / actualYears) - 1.0;
        return BigDecimal.valueOf(cagr).setScale(6, RM);
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
