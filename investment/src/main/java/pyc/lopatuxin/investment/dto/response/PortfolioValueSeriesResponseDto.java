package pyc.lopatuxin.investment.dto.response;

import lombok.Getter;

import java.util.List;

/**
 * Response for the portfolio value-history chart. Adds price-staleness flags on top of the
 * shared {@link SeriesResponseDto} shape: {@code historyPending}/{@code pendingTickers} cover
 * only securities whose price history is still loading (status not READY), while
 * {@code pricesStale}/{@code staleTickers} cover securities whose latest known price is older
 * than the window's end by more than a routine gap — a genuinely different situation the
 * frontend labels differently. Kept as its own DTO rather than adding the fields to
 * {@link SeriesResponseDto} itself so security/price-history and dividends-history responses
 * are unaffected.
 */
@Getter
public class PortfolioValueSeriesResponseDto extends SeriesResponseDto<PortfolioValuePointDto> {

    private final boolean pricesStale;
    private final List<String> staleTickers;

    public PortfolioValueSeriesResponseDto(List<PortfolioValuePointDto> series, boolean historyPending,
                                           List<String> pendingTickers, boolean pricesStale, List<String> staleTickers) {
        super(series, historyPending, pendingTickers);
        this.pricesStale = pricesStale;
        this.staleTickers = staleTickers;
    }
}
