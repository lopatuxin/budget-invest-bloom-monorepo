package pyc.lopatuxin.investment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * How many shares of a ticker a user held on an arbitrary date, replayed from their trade
 * journal instead of read off the current {@code Position} row — a dividend's record date is
 * almost always in the past, and the position only reflects today's holding.
 */
@Slf4j
@Service
public class HoldingsOnDateService {

    // Folds the whole journal into ticker -> its own trades, sorted by execution time, once per
    // page build — quantityAt below is called once per dividend line, and scanning the full,
    // unfiltered journal on every one of those calls made the page build cost payouts × trades
    // instead of payouts × trades-for-that-ticker (plan point 6).
    public Map<String, List<Transaction>> groupSortedByTicker(List<Transaction> journal) {
        return journal.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getSecurity().getTicker(),
                        Collectors.collectingAndThen(Collectors.toList(), HoldingsOnDateService::sortedByExecutedAt)));
    }

    private static List<Transaction> sortedByExecutedAt(List<Transaction> transactions) {
        transactions.sort(Comparator.comparing(Transaction::getExecutedAt));
        return transactions;
    }

    // Strict "<": settlement is T+1, so a trade executed on the record date itself has not
    // settled yet and does not carry that dividend (plan point 1). The record date is the MOEX
    // trading day, so "start of day" is taken in the app's own zone (ZoneId.systemDefault(),
    // set to Europe/Moscow by App.main) — the same zone AnalyticsService.computeValuesByDate
    // uses to replay the same trade journal, so the two holdings calculations agree on which
    // calendar day a trade belongs to.
    public BigDecimal quantityAt(Map<String, List<Transaction>> journalByTicker, String ticker, LocalDate date) {
        List<Transaction> transactions = journalByTicker.getOrDefault(ticker, List.of());
        Instant cutoff = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        BigDecimal quantity = BigDecimal.ZERO;
        for (Transaction transaction : transactions) {
            // Transactions are sorted ascending by executedAt, so the first one at or past the
            // cutoff means every following one is too — safe to stop scanning right there.
            if (!transaction.getExecutedAt().isBefore(cutoff)) {
                break;
            }
            // BUY grows the holding; SELL and REDEMPTION (bond closed at maturity, which reduces
            // quantity exactly like a sale) both shrink it.
            quantity = transaction.getType() == TransactionType.BUY
                    ? quantity.add(transaction.getQuantity())
                    : quantity.subtract(transaction.getQuantity());
        }
        if (quantity.signum() < 0) {
            log.warn("Negative holdings computed for ticker={} on date={}: {} — treating as zero", ticker, date, quantity);
            return BigDecimal.ZERO;
        }
        return quantity;
    }
}
