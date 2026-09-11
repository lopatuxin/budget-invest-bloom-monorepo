package pyc.lopatuxin.budget.util;

import lombok.experimental.UtilityClass;
import pyc.lopatuxin.budget.dto.common.ChangeDto;
import pyc.lopatuxin.budget.entity.enums.NormStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared arithmetic for the "this period against a year/period ago" comparisons used by the
 * overview and analytics pages: percentage change with a {@link NormStatus}, savings rate,
 * and BigDecimal money rounding. Kept in one place so both pages report the same numbers from
 * the same formulas.
 */
@UtilityClass
public class ComparisonMath {

    /**
     * A null or non-positive base has no meaningful percentage: dividing by a negative one flips
     * the sign, so growing from -50000 to +50000 would report -200% and a "falling" badge. Such a
     * change is reported as {@code NO_HISTORY} and the frontend hides the badge instead.
     */
    public ChangeDto changeFrom(BigDecimal delta, BigDecimal base) {
        if (base == null || base.signum() <= 0) {
            return ChangeDto.builder().status(NormStatus.NO_HISTORY).build();
        }
        BigDecimal percent = delta.divide(base, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        return ChangeDto.builder().percent(percent).status(NormStatus.fromDeviationPercent(percent)).build();
    }

    /**
     * Savings rate as (income - expenses) / income * 100, clamped to [-99, 99].
     * Returns null when income is non-positive (the tile shows "—" instead of a misleading 0).
     */
    public Integer savingsRate(BigDecimal income, BigDecimal expenses) {
        if (income == null || income.signum() <= 0) {
            return null;
        }
        int raw = income.subtract(expenses)
                .divide(income, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        return Math.clamp(raw, -99, 99);
    }

    public BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
