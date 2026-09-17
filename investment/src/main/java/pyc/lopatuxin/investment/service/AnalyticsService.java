package pyc.lopatuxin.investment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.investment.dto.response.PortfolioValuePointDto;
import pyc.lopatuxin.investment.dto.response.PortfolioValueSeriesResponseDto;
import pyc.lopatuxin.investment.dto.response.PositionResponseDto;
import pyc.lopatuxin.investment.dto.response.PricePointDto;
import pyc.lopatuxin.investment.dto.response.SeriesResponseDto;
import pyc.lopatuxin.investment.dto.response.SnapshotResult;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
import pyc.lopatuxin.investment.mapper.PositionMapper;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    // Stocks and ETFs trade every session, so a price older than 10 days means the sync
    // genuinely stalled. Bonds and OFZ trade far less often — MoexResponseParser skips a candle
    // with an empty close (illiquid papers routinely have no trades for days), so a similar gap
    // there is normal, not a stalled sync; they get a wider tolerance instead of a permanent
    // "stale" badge on an otherwise healthy portfolio.
    private static final int STALE_TOLERANCE_STOCK_ETF_DAYS = 10;
    private static final int STALE_TOLERANCE_BOND_OFZ_DAYS = 30;

    private final PriceHistoryRepository priceHistoryRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;
    private final TransactionRepository transactionRepository;
    private final SecurityRepository securityRepository;
    private final PositionMapper positionMapper;
    private final PortfolioGroupingService portfolioGroupingService;
    private final BondPricing bondPricing;

    public PortfolioValueSeriesResponseDto portfolioValueHistory(UUID userId, LocalDate from, LocalDate to) {
        List<Position> positions = positionRepository.findByUserIdWithSecurity(userId);
        if (positions.isEmpty()) {
            return new PortfolioValueSeriesResponseDto(List.of(), false, List.of(), false, List.of());
        }

        List<String> pendingTickers = collectPendingAndTrigger(
                positions.stream().map(Position::getSecurity).toList());

        // Merge function guards against two positions on the same ticker (see PortfolioService,
        // which applies the same guard for the same reason) by summing their quantities instead
        // of throwing IllegalStateException.
        Map<String, BigDecimal> quantitiesByTicker = positions.stream()
                .filter(p -> !pendingTickers.contains(p.getSecurity().getTicker()))
                .collect(Collectors.toMap(
                        p -> p.getSecurity().getTicker(),
                        Position::getQuantity,
                        BigDecimal::add
                ));

        if (quantitiesByTicker.isEmpty()) {
            return new PortfolioValueSeriesResponseDto(List.of(), !pendingTickers.isEmpty(), pendingTickers, false, List.of());
        }

        Map<String, Security> securityByTicker = positions.stream()
                .collect(Collectors.toMap(p -> p.getSecurity().getTicker(), Position::getSecurity, (a, b) -> a));

        Set<String> readyTickers = quantitiesByTicker.keySet();
        // Seed with the last close strictly before `from` so a security whose history has no
        // row inside [from, to] (e.g. sync stalled after `from`) still carries its last known
        // price into the window instead of being silently treated as worth 0.
        Map<String, LocalDate> lastKnownDate = new HashMap<>();
        Map<String, BigDecimal> lastKnownClose = seedLastKnownClose(readyTickers, from, lastKnownDate, securityByTicker);

        List<PriceHistory> history = priceHistoryRepository
                .findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(readyTickers, from, to);
        Map<LocalDate, Map<String, BigDecimal>> closePriceByDate = groupCloseByDate(history, securityByTicker);
        // The window's reported start must reflect the same composition as every later point:
        // a ticker whose price only becomes known partway into the window (not seeded before
        // `from`, first real row later) would otherwise read as 0 at `from` while counting in
        // full once its price arrives, making "change over the period" report growth that never
        // happened. Trimming the grid's start to the latest date any ready ticker's price
        // becomes known keeps every reported point comparable.
        LocalDate stableFrom = resolveStableFrom(readyTickers, from, lastKnownClose, closePriceByDate);
        // The date grid must still span the rest of the requested window even when it holds no
        // price row at all (a sync stalled before `from`) or its newest row falls short of `to`
        // (a partial catch-up) — otherwise the series comes back empty, or its line quietly
        // stops at the last date that happens to have a row instead of reaching `to` at the
        // carried-forward price.
        List<LocalDate> sortedDates = buildDateGrid(closePriceByDate.keySet(), stableFrom, to);

        List<PortfolioValuePointDto> series = buildValuePoints(quantitiesByTicker, sortedDates, closePriceByDate, lastKnownClose, lastKnownDate);
        overrideLastPointWithLiveValue(series, to, positions, pendingTickers);

        // A ticker still without any known price (neither before `from` nor inside the window)
        // must not be reported as complete — the series honestly flags its price as stale,
        // same as one whose latest known price is simply old, instead of silently counting it
        // as worth 0.
        List<String> noPriceTickers = readyTickers.stream()
                .filter(t -> !lastKnownClose.containsKey(t))
                .toList();
        // A ticker whose newest known price is older than `to` by more than its routine
        // weekend/holiday (or illiquid-trading) gap has not actually caught up to the requested
        // window's end, even though it does have some price history — same honesty requirement
        // as noPriceTickers, just for a stale rather than a fully missing price.
        List<String> staleTickers = readyTickers.stream()
                .filter(t -> !noPriceTickers.contains(t))
                .filter(t -> lastKnownDate.get(t).isBefore(to.minusDays(staleToleranceDays(securityByTicker.get(t).getType()))))
                .toList();
        List<String> pricesStaleTickers = Stream.concat(noPriceTickers.stream(), staleTickers.stream()).toList();

        return new PortfolioValueSeriesResponseDto(series, !pendingTickers.isEmpty(), pendingTickers,
                !pricesStaleTickers.isEmpty(), pricesStaleTickers);
    }

    private int staleToleranceDays(SecurityType type) {
        return type == SecurityType.BOND || type == SecurityType.OFZ
                ? STALE_TOLERANCE_BOND_OFZ_DAYS : STALE_TOLERANCE_STOCK_ETF_DAYS;
    }

    private LocalDate resolveStableFrom(Set<String> readyTickers, LocalDate from,
                                        Map<String, BigDecimal> seededLastKnownClose,
                                        Map<LocalDate, Map<String, BigDecimal>> closePriceByDate) {
        LocalDate stableFrom = from;
        for (String ticker : readyTickers) {
            if (seededLastKnownClose.containsKey(ticker)) {
                continue;
            }
            LocalDate firstDate = firstAppearance(ticker, closePriceByDate);
            if (firstDate != null && firstDate.isAfter(stableFrom)) {
                stableFrom = firstDate;
            }
        }
        return stableFrom;
    }

    // closePriceByDate is a TreeMap (see groupCloseByDate), so entries are already in date
    // order — the first match found here is genuinely the ticker's earliest known row.
    private LocalDate firstAppearance(String ticker, Map<LocalDate, Map<String, BigDecimal>> closePriceByDate) {
        for (Map.Entry<LocalDate, Map<String, BigDecimal>> entry : closePriceByDate.entrySet()) {
            if (entry.getValue().containsKey(ticker)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // The chart's last point must not disagree with the "Стоимость портфеля" card next to it:
    // that card is computed from live snapshot data (PortfolioGroupingService.computeTotals),
    // while every earlier point here comes from the PriceHistory table, which can lag behind by
    // weeks. Only the very last point — and only when the window actually reaches today — is
    // swapped for the live figure; earlier points still show what PriceHistory honestly says.
    // The live figure is computed over the same composition as the rest of the series — PENDING
    // tickers excluded, same as quantitiesByTicker above — otherwise a PENDING security the
    // exchange happens to answer for would jump into the last point alone, reading as growth
    // that never happened in the rest of the line.
    private void overrideLastPointWithLiveValue(List<PortfolioValuePointDto> series, LocalDate to,
                                                List<Position> positions, List<String> pendingTickers) {
        if (series.isEmpty() || !to.isEqual(LocalDate.now())) {
            return;
        }
        PortfolioValuePointDto lastPoint = series.get(series.size() - 1);
        if (!lastPoint.getDate().isEqual(to)) {
            return;
        }
        List<Position> readyPositions = positions.stream()
                .filter(p -> !pendingTickers.contains(p.getSecurity().getTicker()))
                .toList();
        BigDecimal liveValue = computeLiveTotalValue(readyPositions);
        if (liveValue == null) {
            return;
        }
        series.set(series.size() - 1, PortfolioValuePointDto.builder().date(to).value(liveValue).build());
    }

    // Null when no held ticker has a live price at all (exchange unreachable and nothing
    // cached in price_snapshots either — e.g. right after a fresh deploy) — in that rare case
    // the carried-forward PriceHistory value already in the series is a better last point than
    // a synthetic 0, so the caller leaves it alone instead of overriding.
    private BigDecimal computeLiveTotalValue(List<Position> positions) {
        List<PositionResponseDto> basePositions = positions.stream().map(positionMapper::toDto).toList();
        Set<String> tickers = basePositions.stream().map(PositionResponseDto::getTicker).collect(Collectors.toSet());
        Map<String, SnapshotResult> liveSnapshots = marketDataService.getSnapshots(tickers);
        boolean anyLivePrice = liveSnapshots.values().stream().anyMatch(s -> s.lastPrice() != null);
        if (!anyLivePrice) {
            return null;
        }
        return portfolioGroupingService.computeTotals(basePositions, liveSnapshots).totalValue();
    }

    private Map<String, BigDecimal> seedLastKnownClose(Set<String> tickers, LocalDate from, Map<String, LocalDate> lastKnownDate,
                                                        Map<String, Security> securityByTicker) {
        List<PriceHistory> lastBeforeWindow = priceHistoryRepository.findLastBeforeDateForTickers(tickers, from);
        Map<String, BigDecimal> lastKnownClose = new HashMap<>();
        for (PriceHistory ph : lastBeforeWindow) {
            lastKnownClose.put(ph.getTicker(), toRubles(securityByTicker, ph.getTicker(), ph.getClose()));
            lastKnownDate.put(ph.getTicker(), ph.getTradeDate());
        }
        return lastKnownClose;
    }

    // Every PriceHistory close is the exchange quote as-is — percent-of-par for a BOND/OFZ — so
    // every consumer of this series (the value-history chart, valueAtDates, the price-growth
    // input in ProjectionService) must convert through BondPricing before doing arithmetic on it
    // (plan point 2). Falls back to the raw quote when the ticker's Security is not in the map
    // (should not happen for a ticker this service is already iterating positions/transactions
    // for, but avoids an NPE over a defensive gap rather than a real one).
    private BigDecimal toRubles(Map<String, Security> securityByTicker, String ticker, BigDecimal quoted) {
        Security security = securityByTicker.get(ticker);
        return security != null ? bondPricing.quotedToRubles(security, quoted) : quoted;
    }

    // priceDates can hold dates earlier than `from` (stableFrom) — closePriceByDate is built
    // from the whole [from, to] query result, before stableFrom trims the reported window's
    // start. Those earlier dates must not leak into the grid: they are exactly the dates whose
    // composition is still incomplete, which is what stableFrom exists to exclude.
    private List<LocalDate> buildDateGrid(Set<LocalDate> priceDates, LocalDate from, LocalDate to) {
        Set<LocalDate> grid = priceDates.stream()
                .filter(date -> !date.isBefore(from))
                .collect(Collectors.toCollection(TreeSet::new));
        grid.add(from);
        grid.add(to);
        return List.copyOf(grid);
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
        Map<LocalDate, Map<String, BigDecimal>> closePriceByDate = groupCloseByDate(priceHistory, securityByTicker);
        List<LocalDate> sortedPriceDates = closePriceByDate.keySet().stream().sorted().toList();

        List<LocalDate> sortedDates = dates.stream().sorted().toList();
        Map<String, BigDecimal> lastKnownClose = new HashMap<>();
        Set<String> missingPriceTickers = new HashSet<>();
        Map<LocalDate, BigDecimal> valueByDate = computeValuesByDate(
                sortedDates, readyTransactions, sortedPriceDates, closePriceByDate, lastKnownClose, missingPriceTickers);

        // Same honesty requirement as portfolioValueHistory: a ticker that never got a single
        // price row up to maxDate is silently worth 0 in calcDayValue — the series must not
        // report it as complete. Scoped to tickers actually held (qty > 0) on at least one
        // requested date: a long fully-sold, delisted ticker with no price history never affects
        // any of these sums, so it must not permanently flag it either. Kept apart from
        // historyPending: a READY ticker simply missing a price is a stale-price situation, not
        // a still-loading one, and must not keep the "история ещё загружается" message up
        // forever — see PortfolioValueSeries.pricesStale.
        List<String> staleTickers = List.copyOf(missingPriceTickers);
        boolean pricesStale = !staleTickers.isEmpty();

        List<PortfolioValueAt> points = dates.stream()
                .map(d -> new PortfolioValueAt(d, valueByDate.get(d)))
                .toList();
        return new PortfolioValueSeries(points, historyPending, pricesStale, staleTickers);
    }

    public SeriesResponseDto<PricePointDto> securityPriceHistory(String ticker, LocalDate from, LocalDate to) {
        boolean isPending = isHistoryPending(ticker);
        if (isPending) {
            marketDataService.triggerHistoryAsync(ticker);
            return new SeriesResponseDto<>(List.of(), true, List.of(ticker));
        }
        Security security = securityRepository.findById(ticker).orElse(null);
        List<PriceHistory> history = priceHistoryRepository
                .findByTickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, from, to);
        List<PricePointDto> series = history.stream().map(ph -> toPricePointDto(ph, security)).toList();
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
        return new PortfolioValueSeries(points, historyPending, false, List.of());
    }

    private Map<LocalDate, BigDecimal> computeValuesByDate(
            List<LocalDate> sortedDates,
            List<Transaction> sortedTransactions,
            List<LocalDate> sortedPriceDates,
            Map<LocalDate, Map<String, BigDecimal>> closePriceByDate,
            Map<String, BigDecimal> lastKnownClose,
            Set<String> missingPriceTickers) {

        Map<LocalDate, BigDecimal> result = new HashMap<>();
        Map<String, BigDecimal> quantities = new HashMap<>();
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
            // Checked right here, with quantities and lastKnownClose both already caught up to
            // `date` — not once after the whole loop finishes against the final, ever-growing
            // lastKnownClose. A ticker whose first price arrives on a later date would otherwise
            // look "known" for every earlier date too, silently valuing it at 0 there without
            // ever flagging the series incomplete.
            quantities.forEach((ticker, qty) -> {
                if (qty.compareTo(BigDecimal.ZERO) > 0 && !lastKnownClose.containsKey(ticker)) {
                    missingPriceTickers.add(ticker);
                }
            });
            result.put(date, calcDayValue(quantities, lastKnownClose).setScale(2, RoundingMode.HALF_UP));
        }
        return result;
    }

    private void applyTransaction(Map<String, BigDecimal> quantities, Transaction transaction) {
        String ticker = transaction.getSecurity().getTicker();
        // BUY grows the holding; SELL and REDEMPTION (bond closed at maturity, which reduces
        // quantity exactly like a sale) both shrink it.
        BigDecimal signedQuantity = transaction.getType() == TransactionType.BUY
                ? transaction.getQuantity()
                : transaction.getQuantity().negate();
        quantities.merge(ticker, signedQuantity, BigDecimal::add);
    }

    private Map<LocalDate, Map<String, BigDecimal>> groupCloseByDate(List<PriceHistory> history,
                                                                      Map<String, Security> securityByTicker) {
        Map<LocalDate, Map<String, BigDecimal>> result = new TreeMap<>();
        for (PriceHistory ph : history) {
            result.computeIfAbsent(ph.getTradeDate(), _ -> new HashMap<>())
                    .put(ph.getTicker(), toRubles(securityByTicker, ph.getTicker(), ph.getClose()));
        }
        return result;
    }

    private List<PortfolioValuePointDto> buildValuePoints(
            Map<String, BigDecimal> quantitiesByTicker,
            List<LocalDate> sortedDates,
            Map<LocalDate, Map<String, BigDecimal>> closePriceByDate,
            Map<String, BigDecimal> lastKnownClose,
            Map<String, LocalDate> lastKnownDate) {

        List<PortfolioValuePointDto> points = new ArrayList<>();
        for (LocalDate date : sortedDates) {
            updateLastKnownClose(lastKnownClose, closePriceByDate.get(date), lastKnownDate, date);
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

    private void updateLastKnownClose(Map<String, BigDecimal> lastKnownClose, Map<String, BigDecimal> dayPrices,
                                       Map<String, LocalDate> lastKnownDate, LocalDate date) {
        if (dayPrices == null) {
            return;
        }
        lastKnownClose.putAll(dayPrices);
        for (String ticker : dayPrices.keySet()) {
            lastKnownDate.put(ticker, date);
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

    // Chart series shown to the user (plan point 2) — quoted-to-rubles only, no accrued interest
    // (that only applies to current position/portfolio valuation, see BondPricing).
    private PricePointDto toPricePointDto(PriceHistory ph, Security security) {
        BigDecimal open = security != null ? bondPricing.quotedToRubles(security, ph.getOpen()) : ph.getOpen();
        BigDecimal close = security != null ? bondPricing.quotedToRubles(security, ph.getClose()) : ph.getClose();
        BigDecimal high = security != null ? bondPricing.quotedToRubles(security, ph.getHigh()) : ph.getHigh();
        BigDecimal low = security != null ? bondPricing.quotedToRubles(security, ph.getLow()) : ph.getLow();
        return PricePointDto.builder()
                .date(ph.getTradeDate())
                .open(open)
                .close(close)
                .high(high)
                .low(low)
                .volume(ph.getVolume())
                .build();
    }
}
