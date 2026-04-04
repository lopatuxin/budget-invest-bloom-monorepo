package pyc.lopatuxin.budget.util;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Утилита для форматирования процентного изменения показателей.
 */
@UtilityClass
public class TrendFormatter {

    /**
     * Вычисляет процентное изменение и форматирует в строку вида "+1.4%" или "-3.1%".
     * Если предыдущее значение равно нулю — возвращает "+0.0%".
     */
    public String formatTrend(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return "+0.0%";
        }

        BigDecimal percentChange = current.subtract(previous)
                .divide(previous.abs(), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        String formatted = percentChange.toPlainString() + "%";
        return (percentChange.compareTo(BigDecimal.ZERO) >= 0) ? "+" + formatted : formatted;
    }
}
