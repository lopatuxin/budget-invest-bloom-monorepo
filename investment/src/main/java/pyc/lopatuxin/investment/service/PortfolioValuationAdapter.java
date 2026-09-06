package pyc.lopatuxin.investment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.dto.response.PortfolioOverviewDto;
import pyc.lopatuxin.investment.dto.response.PortfolioPageResponseDto;
import pyc.lopatuxin.investment.dto.response.UpcomingDividendDto;
import pyc.lopatuxin.shared.port.PortfolioCurrentValuation;
import pyc.lopatuxin.shared.port.PortfolioNextDividend;
import pyc.lopatuxin.shared.port.PortfolioValuation;
import pyc.lopatuxin.shared.port.PortfolioValueSeries;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * In-process implementation of the {@link PortfolioValuation} port on the investment side.
 * {@link #current} reads the same snapshot as the investment page ({@link PortfolioService#getPortfolioPage}),
 * {@link #valueAt} delegates to {@link AnalyticsService#valueAtDates}.
 */
@Component
@RequiredArgsConstructor
@Transactional(value = "investmentTransactionManager", readOnly = true)
public class PortfolioValuationAdapter implements PortfolioValuation {

    private final PortfolioService portfolioService;
    private final AnalyticsService analyticsService;

    @Override
    public PortfolioCurrentValuation current(UUID userId) {
        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId);
        PortfolioOverviewDto overview = page.getOverview();
        return new PortfolioCurrentValuation(
                overview.getTotalValue(),
                overview.getTotalCost(),
                overview.getTotalPnl(),
                overview.getAssetsCount(),
                overview.getDividends12m(),
                findNextDividend(page.getUpcomingDividends())
        );
    }

    @Override
    public PortfolioValueSeries valueAt(UUID userId, List<LocalDate> dates) {
        return analyticsService.valueAtDates(userId, dates);
    }

    private PortfolioNextDividend findNextDividend(List<UpcomingDividendDto> upcomingDividends) {
        if (upcomingDividends == null || upcomingDividends.isEmpty()) {
            return null;
        }
        UpcomingDividendDto earliest = upcomingDividends.stream()
                .min(Comparator.comparing(UpcomingDividendDto::getPaymentDate))
                .orElseThrow();
        return new PortfolioNextDividend(
                earliest.getTicker(),
                earliest.getSecurityName(),
                earliest.getPaymentDate(),
                earliest.getTotalAmount()
        );
    }
}
