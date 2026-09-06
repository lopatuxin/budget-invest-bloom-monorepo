package pyc.lopatuxin.budget.service;

import org.springframework.stereotype.Service;
import pyc.lopatuxin.budget.dto.common.NormComparisonDto;
import pyc.lopatuxin.budget.entity.enums.NormStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Pure arithmetic service that compares an actual amount against the user's personal norm
 * ("usual by this day"), built from a list of pre-aggregated monthly rows. Contains no
 * repository dependencies — the caller is responsible for selecting the 12-month window
 * and filtering it down to months that actually have data.
 */
@Service
public class NormCalculationService {

    private static final BigDecimal ABOVE_MUCH_THRESHOLD = new BigDecimal("50");
    private static final BigDecimal ABOVE_THRESHOLD = new BigDecimal("10");
    private static final BigDecimal BELOW_THRESHOLD = new BigDecimal("-10");

    /**
     * Compares the actual amount for the current period against the norm derived from
     * {@code dataMonths}.
     *
     * @param dataMonths  monthly aggregates for the months of the 12-month window that have data
     *                    (a month with no data anywhere is omitted, not zero-filled)
     * @param actualAmount the actual amount for the requested period (expenses, income or one category)
     * @param dayOfMonth  the day of month the current period is compared up to (0 for a future month)
     * @return the norm comparison, or a {@link NormStatus#NO_HISTORY} result when there is no usable history
     */
    public NormComparisonDto calculateNorm(List<MonthlyAggregateRow> dataMonths, BigDecimal actualAmount, int dayOfMonth) {
        if (dataMonths.isEmpty() || dayOfMonth <= 0) {
            return noHistory();
        }

        List<MonthlyAggregateRow> cutoffMonths = monthsFromDailyTrackingStart(dataMonths);
        if (cutoffMonths.isEmpty()) {
            return noHistory();
        }

        BigDecimal usualByDay = average(cutoffMonths, MonthlyAggregateRow::cutoffAmount).setScale(2, RoundingMode.HALF_UP);
        if (usualByDay.compareTo(BigDecimal.ZERO) == 0) {
            return noHistory();
        }

        BigDecimal averageMonthly = average(dataMonths, MonthlyAggregateRow::fullAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deviationPercent = actualAmount.subtract(usualByDay)
                .divide(usualByDay, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        return NormComparisonDto.builder()
                .usualByDay(usualByDay)
                .averageMonthly(averageMonthly)
                .deviationPercent(deviationPercent)
                .status(resolveStatus(deviationPercent))
                .build();
    }

    private NormComparisonDto noHistory() {
        return NormComparisonDto.builder().status(NormStatus.NO_HISTORY).build();
    }

    /**
     * Restricts {@code dataMonths} to the earliest daily-tracked month (inclusive) onward: the point
     * where the user's history stops being one lump sum per month and starts recording individual days.
     * A month with a single day of records that comes after that point still counts. Returns an empty
     * list when no month in {@code dataMonths} has day-level records, meaning {@code usualByDay} cannot
     * be computed at all.
     */
    private List<MonthlyAggregateRow> monthsFromDailyTrackingStart(List<MonthlyAggregateRow> dataMonths) {
        Optional<YearMonth> dailyTrackingStart = dataMonths.stream()
                .filter(MonthlyAggregateRow::dailyGranularity)
                .map(row -> YearMonth.of(row.year(), row.month()))
                .min(Comparator.naturalOrder());

        if (dailyTrackingStart.isEmpty()) {
            return List.of();
        }

        return dataMonths.stream()
                .filter(row -> !YearMonth.of(row.year(), row.month()).isBefore(dailyTrackingStart.get()))
                .toList();
    }

    private BigDecimal average(List<MonthlyAggregateRow> rows, Function<MonthlyAggregateRow, BigDecimal> extractor) {
        BigDecimal total = rows.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(rows.size()), 10, RoundingMode.HALF_UP);
    }

    private NormStatus resolveStatus(BigDecimal deviationPercent) {
        if (deviationPercent.compareTo(ABOVE_MUCH_THRESHOLD) > 0) {
            return NormStatus.ABOVE_MUCH;
        }
        if (deviationPercent.compareTo(ABOVE_THRESHOLD) > 0) {
            return NormStatus.ABOVE;
        }
        if (deviationPercent.compareTo(BELOW_THRESHOLD) < 0) {
            return NormStatus.BELOW;
        }
        return NormStatus.NORMAL;
    }

    /**
     * One month of the history window that has data: the sum up to the compared day
     * ({@code cutoffAmount}), the sum for the full month ({@code fullAmount}), and whether the
     * month's records are spread over more than one calendar day ({@code dailyGranularity}) —
     * a single-day month is a legacy monthly-lump-sum entry rather than day-by-day tracking.
     *
     * @param year             calendar year of the month
     * @param month            month number (1-12)
     * @param cutoffAmount     sum of records with day-of-month <= the compared day
     * @param fullAmount       sum of records for the whole month
     * @param dailyGranularity true if the month's records fall on more than one distinct day
     */
    public record MonthlyAggregateRow(int year, int month, BigDecimal cutoffAmount, BigDecimal fullAmount,
                                       boolean dailyGranularity) {
    }
}
