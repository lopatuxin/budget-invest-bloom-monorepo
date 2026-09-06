package pyc.lopatuxin.shared.port;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * In-process port for valuing a user's investment portfolio.
 * Implemented by the investment module, called by the budget module to build the
 * overview page's capital section without an HTTP round trip.
 */
public interface PortfolioValuation {

    /** Current portfolio valuation (value, cost, pnl, dividends), as shown on the investment page. */
    PortfolioCurrentValuation current(UUID userId);

    /** Portfolio value at each of the given dates, computed from trade history and price history. */
    PortfolioValueSeries valueAt(UUID userId, List<LocalDate> dates);
}
