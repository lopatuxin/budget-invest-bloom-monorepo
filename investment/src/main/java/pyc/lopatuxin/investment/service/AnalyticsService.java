package pyc.lopatuxin.investment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.investment.dto.response.PaidDividendDto;
import pyc.lopatuxin.investment.dto.response.PortfolioValuePointDto;
import pyc.lopatuxin.investment.dto.response.PricePointDto;
import pyc.lopatuxin.investment.dto.response.SeriesResponseDto;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.repository.TransactionRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;
import pyc.lopatuxin.shared.port.PortfolioValueAt;
import pyc.lopatuxin.shared.port.PortfolioValueSeries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
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

    private final PriceHistoryRepository priceHistoryRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;
    private final DividendRepository dividendRepository;
    private final TransactionRepository transactionRepository;

    public List<PaidDividendDto> securityDividendsHistory(String ticker) {
        return dividendRepository.findPaidByTickerWithSecurity(ticker).stream()
                .map(this::toPaidDividendDto)
                .toList();
    }

    public SeriesResponseDto<PortfolioValuePointDto> portfolioValueHistory(UUID userId, LocalDate from, LocalDate to) {
        List<Position> positions = positionRepository.findByUserIdWithSecurity(userId);
        if (positions.isEmpty()) {
            return new SeriesResponseDto<>(List.of(), false, List.of());
        }

        List<String> pendingTickers = collectPendingAndTrigger(
                positions.stream().map(Position::getSecurity).toList());

        Map<String, BigDecimal> quantitiesByTicker = positions.stream()
                .filter(p -> !pendingTickers.contains(p.getSecurity().getTicker()))
                .collect(Collectors.toMap(
                        p -> p.getSecurity().getTicker(),
                        Position::getQuantity
                ));

        if (quantitiesByTicker.isEmpty()) {
            return new SeriesResponseDto<>(List.of(), !pendingTickers.isEmpty(), pendingTickers);
        }

        Set<String> readyTickers = quantitiesByTicker.keySet();
        List<PriceHistory> history = priceHistoryRepository
                .findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(readyTickers, from, to);
        if (history.isEmpty()) {
            return new SeriesResponseDto<>(List.of(), !pendingTickers.isEmpty(), pendingTickers);
        }

        Map<LocalDate, Map<String, BigDecimal>> closePriceByDate = groupCloseByDate(history);
        List<LocalDate> sortedDates = closePriceByDate.keySet().stream().sorted().toList();

        Map<String, BigDecimal> lastKnownClose = new HashMap<>();
        List<PortfolioValuePointDto> series = buildValuePoints(quantitiesByTicker, sortedDates, closePriceByDate, lastKnownClose);
        return new SeriesResponseDto<>(series, !pendingTickers.isEmpty(), pendingTickers);
    }

    public PortfolioValueSeries valueAtDates(UUID userId, List<LocalDate> dates) {
        List<Transaction> transactions = transactionRepository.findByUserIdWithSecurity(userId);
        if (transactions.isEmpty()) {
            return zeroSeries(dates, false);
        }

        Map<String, Security> securityByTicker = transactions.stream()
                .collect(Collectors.toMap(t -> t.getSecurity().getTicker(), Transaction::getSecurity, (a, b) -> a));
        List<String> pendingTickers = collectPendingAndTrigger(securityByTicker.values());
        boolean historyPending = !pendingTickers.isEmpty();

        List<Transaction> readyTransactions = transactions.stream()
                .filter(t -> !pendingTickers.contains(t.getSecurity().getTicker()))
                .sorted(Comparator.comparing(Transaction::getExecutedAt))
                .toList();
        if (readyTransactions.isEmpty()) {
            return zeroSeries(dates, historyPending);
        }

        Set<String> readyTickers = readyTransactions.stream()
                .map(t -> t.getSecurity().getTicker())
                .collect(Collectors.toSet());
        LocalDate earliestTxDate = readyTransactions.get(0).getExecutedAt()
                .atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate maxDate = dates.stream().max(Comparator.naturalOrder()).orElseThrow();
        List<PriceHistory> priceHistory = priceHistoryRepository
                .findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(readyTickers, earliestTxDate, maxDate);
        Map<LocalDate, Map<String, BigDecimal>> closePriceByDate = groupCloseByDate(priceHistory);
        List<LocalDate> sortedPriceDates = closePriceByDate.keySet().stream().sorted().toList();

        List<LocalDate> sortedDates = dates.stream().sorted().toList();
        Map<LocalDate, BigDecimal> valueByDate =
                computeValuesByDate(sortedDates, readyTransactions, sortedPriceDates, closePriceByDate);

        List<PortfolioValueAt> points = dates.stream()
                .map(d -> new PortfolioValueAt(d, valueByDate.get(d)))
                .toList();
        return new PortfolioValueSeries(points, historyPending);
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

    private List<String> collectPendingAndTrigger(Collection<Security> securities) {
        List<String> pending = new ArrayList<>();
        for (Security security : securities) {
            if (security.getHistoryStatus() == HistoryStatus.PENDING) {
                marketDataService.triggerHistoryAsync(security.getTicker());
                pending.add(security.getTicker());
            }
        }
        return pending;
    }

    private PortfolioValueSeries zeroSeries(List<LocalDate> dates, boolean historyPending) {
        List<PortfolioValueAt> points = dates.stream()
                .map(d -> new PortfolioValueAt(d, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)))
                .toList();
        return new PortfolioValueSeries(points, historyPending);
    }

    private Map<LocalDate, BigDecimal> computeValuesByDate(
            List<LocalDate> sortedDates,
            List<Transaction> sortedTransactions,
            List<LocalDate> sortedPriceDates,
            Map<LocalDate, Map<String, BigDecimal>> closePriceByDate) {

        Map<LocalDate, BigDecimal> result = new HashMap<>();
        Map<String, BigDecimal> quantities = new HashMap<>();
        Map<String, BigDecimal> lastKnownClose = new HashMap<>();
        ZoneId zone = ZoneId.systemDefault();
        int txIndex = 0;
        int priceIndex = 0;

        for (LocalDate date : sortedDates) {
            Instant endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant();
            while (txIndex < sortedTransactions.size()
                    && sortedTransactions.get(txIndex).getExecutedAt().isBefore(endOfDay)) {
                applyTransaction(quantities, sortedTransactions.get(txIndex));
                txIndex++;
            }
            while (priceIndex < sortedPriceDates.size() && !sortedPriceDates.get(priceIndex).isAfter(date)) {
                updateLastKnownClose(lastKnownClose, closePriceByDate.get(sortedPriceDates.get(priceIndex)));
                priceIndex++;
            }
            result.put(date, calcDayValue(quantities, lastKnownClose).setScale(2, RoundingMode.HALF_UP));
        }
        return result;
    }

    private void applyTransaction(Map<String, BigDecimal> quantities, Transaction transaction) {
        String ticker = transaction.getSecurity().getTicker();
        BigDecimal signedQuantity = transaction.getType() == TransactionType.SELL
                ? transaction.getQuantity().negate()
                : transaction.getQuantity();
        quantities.merge(ticker, signedQuantity, BigDecimal::add);
    }

    private Map<LocalDate, Map<String, BigDecimal>> groupCloseByDate(List<PriceHistory> history) {
        Map<LocalDate, Map<String, BigDecimal>> result = new TreeMap<>();
        for (PriceHistory ph : history) {
            result.computeIfAbsent(ph.getTradeDate(), _ -> new HashMap<>())
                    .put(ph.getTicker(), ph.getClose());
        }
        return result;
    }

    private List<PortfolioValuePointDto> buildValuePoints(
            Map<String, BigDecimal> quantitiesByTicker,
            List<LocalDate> sortedDates,
            Map<LocalDate, Map<String, BigDecimal>> closePriceByDate,
            Map<String, BigDecimal> lastKnownClose) {

        List<PortfolioValuePointDto> points = new ArrayList<>();
        for (LocalDate date : sortedDates) {
            updateLastKnownClose(lastKnownClose, closePriceByDate.get(date));
            BigDecimal dayValue = calcDayValue(quantitiesByTicker, lastKnownClose);
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

    private BigDecimal calcDayValue(Map<String, BigDecimal> quantities, Map<String, BigDecimal> lastKnownClose) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : quantities.entrySet()) {
            BigDecimal qty = entry.getValue();
            BigDecimal price = lastKnownClose.get(entry.getKey());
            if (qty.compareTo(BigDecimal.ZERO) <= 0 || price == null) {
                continue;
            }
            total = total.add(qty.multiply(price));
        }
        return total;
    }

    private PaidDividendDto toPaidDividendDto(Dividend d) {
        return PaidDividendDto.builder()
                .ticker(d.getSecurity().getTicker())
                .recordDate(d.getRecordDate())
                .paymentDate(d.getPaymentDate())
                .amountPerShare(d.getAmountPerShare())
                .currency(d.getCurrency())
                .build();
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
