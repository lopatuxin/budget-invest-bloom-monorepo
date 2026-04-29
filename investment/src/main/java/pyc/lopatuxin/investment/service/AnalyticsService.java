package pyc.lopatuxin.investment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.investment.dto.response.PortfolioValuePointDto;
import pyc.lopatuxin.investment.dto.response.PricePointDto;
import pyc.lopatuxin.investment.dto.response.SeriesResponseDto;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.repository.TransactionRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TransactionRepository transactionRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;

    public SeriesResponseDto<PortfolioValuePointDto> portfolioValueHistory(UUID userId, LocalDate from, LocalDate to) {
        List<Transaction> txns = transactionRepository.findByUserIdWithSecurity(userId).stream()
                .sorted(Comparator.comparing(Transaction::getExecutedAt))
                .toList();
        if (txns.isEmpty()) {
            return new SeriesResponseDto<>(List.of(), false, List.of());
        }

        List<String> pendingTickers = collectPendingAndTrigger(userId);

        Set<String> readyTickers = collectTickers(txns).stream()
                .filter(t -> !pendingTickers.contains(t))
                .collect(Collectors.toSet());

        if (readyTickers.isEmpty()) {
            return new SeriesResponseDto<>(List.of(), !pendingTickers.isEmpty(), pendingTickers);
        }

        List<PriceHistory> history = priceHistoryRepository
                .findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(readyTickers, from, to);
        if (history.isEmpty()) {
            return new SeriesResponseDto<>(List.of(), !pendingTickers.isEmpty(), pendingTickers);
        }

        Map<LocalDate, Map<String, BigDecimal>> closePriceByDate = groupCloseByDate(history);
        List<LocalDate> sortedDates = closePriceByDate.keySet().stream().sorted().toList();

        Map<String, BigDecimal> lastKnownClose = new HashMap<>();
        List<PortfolioValuePointDto> series = buildValuePoints(txns, sortedDates, closePriceByDate, lastKnownClose);
        return new SeriesResponseDto<>(series, !pendingTickers.isEmpty(), pendingTickers);
    }

    public SeriesResponseDto<PricePointDto> securityPriceHistory(String ticker, LocalDate from, LocalDate to) {
        boolean isPending = isHistoryPending(ticker);
        if (isPending) {
            marketDataService.triggerHistoryAsync(ticker);
            return new SeriesResponseDto<>(List.of(), true, List.of(ticker));
        }
        List<PriceHistory> history = priceHistoryRepository
                .findByTickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, from, to);
        List<PricePointDto> series = history.stream().map(this::toPricePointDto).toList();
        return new SeriesResponseDto<>(series, false, List.of());
    }

    private boolean isHistoryPending(String ticker) {
        return marketDataService.getSecurityHistoryStatus(ticker) == HistoryStatus.PENDING;
    }

    private List<String> collectPendingAndTrigger(UUID userId) {
        List<Position> positions = positionRepository.findByUserIdWithSecurity(userId);
        List<String> pending = new ArrayList<>();
        for (Position pos : positions) {
            String ticker = pos.getSecurity().getTicker();
            if (pos.getSecurity().getHistoryStatus() == HistoryStatus.PENDING) {
                marketDataService.triggerHistoryAsync(ticker);
                pending.add(ticker);
            }
        }
        return pending;
    }

    private Set<String> collectTickers(List<Transaction> txns) {
        return txns.stream()
                .map(t -> t.getSecurity().getTicker())
                .collect(Collectors.toSet());
    }

    private Map<LocalDate, Map<String, BigDecimal>> groupCloseByDate(List<PriceHistory> history) {
        Map<LocalDate, Map<String, BigDecimal>> result = new TreeMap<>();
        for (PriceHistory ph : history) {
            result.computeIfAbsent(ph.getTradeDate(), k -> new HashMap<>())
                    .put(ph.getTicker(), ph.getClose());
        }
        return result;
    }

    private List<PortfolioValuePointDto> buildValuePoints(
            List<Transaction> txns,
            List<LocalDate> sortedDates,
            Map<LocalDate, Map<String, BigDecimal>> closePriceByDate,
            Map<String, BigDecimal> lastKnownClose) {

        List<PortfolioValuePointDto> points = new ArrayList<>();
        for (LocalDate date : sortedDates) {
            updateLastKnownClose(lastKnownClose, closePriceByDate.get(date));
            Map<String, BigDecimal> quantities = calcQuantitiesAtEndOfDay(txns, date);
            BigDecimal dayValue = calcDayValue(quantities, lastKnownClose);
            if (dayValue.compareTo(BigDecimal.ZERO) > 0) {
                points.add(PortfolioValuePointDto.builder().date(date).value(dayValue).build());
            }
        }
        return points;
    }

    private void updateLastKnownClose(Map<String, BigDecimal> lastKnownClose, Map<String, BigDecimal> dayPrices) {
        if (dayPrices != null) {
            lastKnownClose.putAll(dayPrices);
        }
    }

    private Map<String, BigDecimal> calcQuantitiesAtEndOfDay(List<Transaction> txns, LocalDate date) {
        long endOfDayEpoch = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        Map<String, BigDecimal> quantities = new HashMap<>();
        for (Transaction t : txns) {
            if (t.getExecutedAt().toEpochMilli() >= endOfDayEpoch) continue;
            String ticker = t.getSecurity().getTicker();
            BigDecimal current = quantities.getOrDefault(ticker, BigDecimal.ZERO);
            if (t.getType() == TransactionType.BUY) {
                quantities.put(ticker, current.add(t.getQuantity()));
            } else {
                quantities.put(ticker, current.subtract(t.getQuantity()));
            }
        }
        return quantities;
    }

    private BigDecimal calcDayValue(Map<String, BigDecimal> quantities, Map<String, BigDecimal> lastKnownClose) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : quantities.entrySet()) {
            BigDecimal qty = entry.getValue();
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal price = lastKnownClose.get(entry.getKey());
            if (price == null) continue;
            total = total.add(qty.multiply(price));
        }
        return total;
    }

    private PricePointDto toPricePointDto(PriceHistory ph) {
        return PricePointDto.builder()
                .date(ph.getTradeDate())
                .open(ph.getOpen())
                .close(ph.getClose())
                .high(ph.getHigh())
                .low(ph.getLow())
                .volume(ph.getVolume())
                .build();
    }
}
