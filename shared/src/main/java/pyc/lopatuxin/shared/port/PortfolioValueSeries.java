package pyc.lopatuxin.shared.port;

import java.util.List;

/**
 * A series of portfolio value points. {@code historyPending} is true when at least one held
 * security's price history is genuinely still loading (status not READY). {@code pricesStale}
 * is a different situation — a ready security with no known price for part or all of the
 * requested range — kept separate so a page does not show "история ещё загружается" forever
 * for a security that will never leave that state.
 */
public record PortfolioValueSeries(
        List<PortfolioValueAt> points,
        boolean historyPending,
        boolean pricesStale,
        List<String> staleTickers
) {
}
