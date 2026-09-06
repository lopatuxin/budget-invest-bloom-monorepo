package pyc.lopatuxin.shared.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Portfolio value at one point in time. */
public record PortfolioValueAt(
        LocalDate date,
        BigDecimal value
) {
}
