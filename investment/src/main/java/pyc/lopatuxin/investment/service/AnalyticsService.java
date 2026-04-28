package pyc.lopatuxin.investment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.investment.dto.response.PortfolioValuePointDto;
import pyc.lopatuxin.investment.dto.response.PricePointDto;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
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
    private final MarketDataService marketDataService;

    public List<PortfolioValuePointDto> portfolioValueHistory(UUID userId, LocalDate from, LocalDate to) {
        List<Transaction> txns = transactionRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Transaction::getExecutedAt))
                .toList();
        if (txns.isEmpty()) return List.of();

        Set<String> tickers = collectTickers(txns);
        tickers.forEach(marketDataService::ensureHistory);

        List<PriceHistory> history = priceHistoryRepository
                .findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(tickers, from, to);
        if (history.isEmpty()) return List.of();

        Map<LocalDate, Map<String, BigDecimal>> closePriceByDate = groupCloseByDate(history);
        List<LocalDate> sortedDates = closePriceByDate.keySet().stream().sorted().toList();

        Map<String, BigDecimal> lastKnownClose = new HashMap<>();
        return buildValuePoints(txns, sortedDates, closePriceByDate, lastKnownClose);
    }

    public List<PricePointDto> securityPriceHistory(String ticker, LocalDate from, LocalDate to) {
        marketDataService.ensureHistory(ticker);
        List<PriceHistory> history = priceHistoryRepository
                .findByTickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, from, to);
        return history.stream().map(this::toPricePointDto).toList();
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
