package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.entity.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HoldingsOnDateServiceTest — восстановление количества бумаг на дату отсечки по журналу сделок")
class HoldingsOnDateServiceTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final HoldingsOnDateService service = new HoldingsOnDateService();

    private TimeZone originalDefaultTimeZone;

    // quantityAt() derives the cutoff from ZoneId.systemDefault() (App.main sets it to
    // Europe/Moscow in production). Pinning it here makes the tests assert real Moscow calendar
    // days regardless of the timezone of the machine running the build.
    @BeforeEach
    void pinDefaultTimeZoneToMoscow() {
        originalDefaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(MOSCOW));
    }

    @AfterEach
    void restoreDefaultTimeZone() {
        TimeZone.setDefault(originalDefaultTimeZone);
    }

    @Test
    @DisplayName("quantityAt — бумага куплена после отсечки → ноль")
    void quantityAt_boughtAfterCutoff_returnsZero() {
        List<Transaction> journal = List.of(
                buy("NVTK", "8", "2025-10-10")
        );

        BigDecimal result = service.quantityAt(service.groupSortedByTicker(journal), "NVTK", LocalDate.of(2025, 10, 6));

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("quantityAt — бумага куплена до отсечки → полное количество")
    void quantityAt_boughtBeforeCutoff_returnsFullQuantity() {
        List<Transaction> journal = List.of(
                buy("SBERP", "17", "2026-01-05")
        );

        BigDecimal result = service.quantityAt(service.groupSortedByTicker(journal), "SBERP", LocalDate.of(2026, 7, 20));

        assertThat(result).isEqualByComparingTo("17");
    }

    @Test
    @DisplayName("quantityAt — докупка между двумя отсечками → у каждой своё количество")
    void quantityAt_additionalPurchaseBetweenCutoffs_eachCutoffGetsItsOwnQuantity() {
        List<Transaction> journal = List.of(
                buy("SBERP", "17", "2026-01-05"),
                buy("SBERP", "7", "2026-05-01")
        );

        assertThat(service.quantityAt(service.groupSortedByTicker(journal), "SBERP", LocalDate.of(2026, 1, 20))).isEqualByComparingTo("17");
        assertThat(service.quantityAt(service.groupSortedByTicker(journal), "SBERP", LocalDate.of(2026, 7, 20))).isEqualByComparingTo("24");
    }

    @Test
    @DisplayName("quantityAt — продана целиком до отсечки → ноль, но прошлая выплата на более раннюю дату остаётся полной")
    void quantityAt_soldInFullBeforeCutoff_zeroAtLaterCutoff_fullAtEarlierOne() {
        List<Transaction> journal = List.of(
                buy("LKOH", "2", "2025-01-10"),
                sell("LKOH", "2", "2025-06-01")
        );

        assertThat(service.quantityAt(service.groupSortedByTicker(journal), "LKOH", LocalDate.of(2025, 3, 1))).isEqualByComparingTo("2");
        assertThat(service.quantityAt(service.groupSortedByTicker(journal), "LKOH", LocalDate.of(2025, 12, 1))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("quantityAt — продана и куплена снова → количество на отсечку после повторной покупки учитывает обе сделки")
    void quantityAt_soldThenBoughtAgain_reflectsBothTrades() {
        List<Transaction> journal = List.of(
                buy("ROSN", "10", "2025-01-10"),
                sell("ROSN", "10", "2025-06-01"),
                buy("ROSN", "26", "2025-09-01")
        );

        assertThat(service.quantityAt(service.groupSortedByTicker(journal), "ROSN", LocalDate.of(2025, 7, 1))).isEqualByComparingTo("0");
        assertThat(service.quantityAt(service.groupSortedByTicker(journal), "ROSN", LocalDate.of(2026, 1, 12))).isEqualByComparingTo("26");
    }

    @Test
    @DisplayName("quantityAt — сделка ровно в день отсечки не учитывается (строгое \"<\", расчёты T+1)")
    void quantityAt_tradeExactlyOnCutoffDate_notCounted() {
        List<Transaction> journal = List.of(
                buy("NVTK", "8", "2025-10-06")
        );

        BigDecimal result = service.quantityAt(service.groupSortedByTicker(journal), "NVTK", LocalDate.of(2025, 10, 6));

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("quantityAt — дробное количество считается как есть")
    void quantityAt_fractionalQuantity_keptAsIs() {
        List<Transaction> journal = List.of(
                buy("FXRL", "1.5", "2025-01-10")
        );

        BigDecimal result = service.quantityAt(service.groupSortedByTicker(journal), "FXRL", LocalDate.of(2025, 6, 1));

        assertThat(result).isEqualByComparingTo("1.5");
    }

    @Test
    @DisplayName("quantityAt — отрицательный остаток (данные несогласованы) → ноль вместо отрицательного числа")
    void quantityAt_negativeBalance_clampedToZero() {
        List<Transaction> journal = List.of(
                sell("GAZP", "5", "2025-01-10")
        );

        BigDecimal result = service.quantityAt(service.groupSortedByTicker(journal), "GAZP", LocalDate.of(2025, 6, 1));

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("quantityAt — сделка в день отсечки в 01:00 по Москве не учитывается (не уезжает в предыдущие сутки UTC)")
    void quantityAt_tradeAt1amMoscowOnCutoffDate_notCounted() {
        List<Transaction> journal = List.of(
                buyAt("NVTK", "8", LocalDate.of(2025, 10, 6), LocalTime.of(1, 0))
        );

        BigDecimal result = service.quantityAt(service.groupSortedByTicker(journal), "NVTK", LocalDate.of(2025, 10, 6));

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("quantityAt — сделки по другим тикерам в журнале не влияют на результат")
    void quantityAt_ignoresTransactionsForOtherTickers() {
        List<Transaction> journal = List.of(
                buy("SBER", "100", "2025-01-10"),
                buy("GAZP", "50", "2025-01-10")
        );

        BigDecimal result = service.quantityAt(service.groupSortedByTicker(journal), "SBER", LocalDate.of(2025, 6, 1));

        assertThat(result).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("quantityAt — REDEMPTION уменьшает количество как SELL, а не увеличивает (регрессия на баг «непокупка → плюс»)")
    void quantityAt_redemptionReducesQuantity_likeSell() {
        List<Transaction> journal = List.of(
                buy("SU26219RMFS4", "71", "2025-01-10"),
                redemption("SU26219RMFS4", "71", "2026-09-16")
        );

        BigDecimal result = service.quantityAt(service.groupSortedByTicker(journal), "SU26219RMFS4", LocalDate.of(2026, 12, 1));

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("groupSortedByTicker — раскладывает журнал по тикерам, каждая бумага — по дате сделки")
    void groupSortedByTicker_groupsByTickerAndSortsByExecutedAt() {
        Transaction sberLater = buy("SBER", "10", "2025-06-01");
        Transaction sberEarlier = buy("SBER", "5", "2025-01-10");
        Transaction gazp = buy("GAZP", "50", "2025-01-10");
        List<Transaction> journal = List.of(sberLater, gazp, sberEarlier);

        var grouped = service.groupSortedByTicker(journal);

        assertThat(grouped.keySet()).containsExactlyInAnyOrder("SBER", "GAZP");
        assertThat(grouped.get("SBER")).containsExactly(sberEarlier, sberLater);
        assertThat(grouped.get("GAZP")).containsExactly(gazp);
    }

    private Transaction buy(String ticker, String quantity, String executedAtDate) {
        return buyAt(ticker, quantity, LocalDate.parse(executedAtDate), LocalTime.NOON);
    }

    private Transaction sell(String ticker, String quantity, String executedAtDate) {
        return transaction(ticker, quantity, LocalDate.parse(executedAtDate), LocalTime.NOON, TransactionType.SELL);
    }

    private Transaction redemption(String ticker, String quantity, String executedAtDate) {
        return transaction(ticker, quantity, LocalDate.parse(executedAtDate), LocalTime.NOON, TransactionType.REDEMPTION);
    }

    private Transaction buyAt(String ticker, String quantity, LocalDate executedAtDate, LocalTime executedAtTime) {
        return transaction(ticker, quantity, executedAtDate, executedAtTime, TransactionType.BUY);
    }

    private Transaction transaction(String ticker, String quantity, LocalDate executedAtDate, LocalTime executedAtTime, TransactionType type) {
        Security security = Security.builder().ticker(ticker).name(ticker).type(SecurityType.STOCK).build();
        return Transaction.builder()
                .security(security)
                .type(type)
                .quantity(new BigDecimal(quantity))
                .price(BigDecimal.TEN)
                .executedAt(executedAtDate.atTime(executedAtTime).atZone(MOSCOW).toInstant())
                .build();
    }
}
