package pyc.lopatuxin.shared.port;

import java.util.List;

/**
 * A series of portfolio value points. {@code historyPending} is true when at least one held
 * security's price history is still loading, so the series excludes it and may still change.
 */
public record PortfolioValueSeries(
        List<PortfolioValueAt> points,
        boolean historyPending
) {
}
