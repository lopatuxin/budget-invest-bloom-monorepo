package pyc.lopatuxin.investment.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.dto.response.PortfolioOverviewDto;
import pyc.lopatuxin.investment.dto.response.PositionResponseDto;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.mapper.PositionMapper;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;
import pyc.lopatuxin.investment.service.market.dto.SnapshotResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PositionRepository positionRepository;
    private final PositionMapper positionMapper;
    private final MarketDataService marketDataService;
    private final DividendRepository dividendRepository;

    @Transactional(readOnly = true)
    public List<PositionResponseDto> listPositions(UUID userId) {
        List<Position> positions = positionRepository.findByUserIdWithSecurity(userId);
        Set<String> tickers = positions.stream()
                .map(p -> p.getSecurity().getTicker())
                .collect(Collectors.toSet());
        Map<String, SnapshotResult> snapshots = marketDataService.getSnapshots(tickers);
        return positions.stream()
                .map(p -> enrichPosition(positionMapper.toDto(p), p, snapshots))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PositionResponseDto getByTicker(UUID userId, String ticker) {
        Position position = positionRepository.findByUserIdAndSecurity_Ticker(userId, ticker.toUpperCase())
                .orElseThrow(() -> new EntityNotFoundException("Position not found: " + ticker));
        PositionResponseDto dto = positionMapper.toDto(position);
        SnapshotResult snapshot = marketDataService.getSnapshot(ticker.toUpperCase());
        return applySnapshot(dto, position.getQuantity(), snapshot);
    }

    @Transactional(readOnly = true)
    public PortfolioOverviewDto getOverview(UUID userId) {
        List<Position> positions = positionRepository.findByUserIdWithSecurity(userId);
        if (positions.isEmpty()) {
            return PortfolioOverviewDto.builder()
                    .totalValue(BigDecimal.ZERO)
                    .totalCost(BigDecimal.ZERO)
                    .totalPnl(BigDecimal.ZERO)
                    .dailyPnl(BigDecimal.ZERO)
                    .assetsCount(0)
                    .dividends12m(BigDecimal.ZERO)
                    .build();
        }
        Set<String> tickers = positions.stream()
                .map(p -> p.getSecurity().getTicker())
                .collect(Collectors.toSet());
        Map<String, SnapshotResult> snapshots = marketDataService.getSnapshots(tickers);
        Map<String, BigDecimal> quantityByTicker = positions.stream()
                .collect(Collectors.toMap(p -> p.getSecurity().getTicker(), Position::getQuantity));
        BigDecimal dividends12m = calcDividends12m(tickers, quantityByTicker);
        return buildOverview(positions, snapshots, dividends12m);
    }

    private PositionResponseDto enrichPosition(PositionResponseDto dto, Position position,
                                               Map<String, SnapshotResult> snapshots) {
        SnapshotResult snapshot = snapshots.get(position.getSecurity().getTicker());
        return applySnapshot(dto, position.getQuantity(), snapshot);
    }

    private PositionResponseDto applySnapshot(PositionResponseDto dto, BigDecimal quantity,
                                              SnapshotResult snapshot) {
        if (snapshot == null || snapshot.lastPrice() == null) {
            return dto;
        }
        BigDecimal currentPrice = snapshot.lastPrice();
        BigDecimal pnl = currentPrice.subtract(dto.getAveragePrice())
                .multiply(quantity)
                .setScale(2, RoundingMode.HALF_UP);
        dto.setCurrentPrice(currentPrice);
        dto.setPnl(pnl);
        return dto;
    }

    private BigDecimal calcDividends12m(Set<String> tickers, Map<String, BigDecimal> quantityByTicker) {
        LocalDate now = LocalDate.now();
        List<Dividend> dividends = dividendRepository
                .findByTickerInAndPaymentDateBetweenWithSecurity(tickers, now.minusYears(1), now);
        BigDecimal total = BigDecimal.ZERO;
        for (Dividend d : dividends) {
            if (d.getAmountPerShare() == null) continue;
            String ticker = d.getSecurity().getTicker();
            BigDecimal qty = quantityByTicker.getOrDefault(ticker, BigDecimal.ZERO);
            total = total.add(d.getAmountPerShare().multiply(qty));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private PortfolioOverviewDto buildOverview(List<Position> positions, Map<String, SnapshotResult> snapshots,
                                               BigDecimal dividends12m) {
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal dailyPnl = BigDecimal.ZERO;

        for (Position p : positions) {
            totalCost = totalCost.add(p.getTotalCost());
            SnapshotResult snap = snapshots.get(p.getSecurity().getTicker());
            if (snap != null && snap.lastPrice() != null) {
                totalValue = totalValue.add(snap.lastPrice().multiply(p.getQuantity()));
                if (snap.previousClose() != null) {
                    BigDecimal dailyChange = snap.lastPrice().subtract(snap.previousClose())
                            .multiply(p.getQuantity());
                    dailyPnl = dailyPnl.add(dailyChange);
                }
            }
        }

        BigDecimal totalPnl = totalValue.subtract(totalCost).setScale(2, RoundingMode.HALF_UP);
        return PortfolioOverviewDto.builder()
                .totalValue(totalValue.setScale(2, RoundingMode.HALF_UP))
                .totalCost(totalCost.setScale(2, RoundingMode.HALF_UP))
                .totalPnl(totalPnl)
                .dailyPnl(dailyPnl.setScale(2, RoundingMode.HALF_UP))
                .assetsCount(positions.size())
                .dividends12m(dividends12m)
                .build();
    }
}
