package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DividendTimingTest — правило «предстоящий/полученный» и «дата получения», общее для портфеля и страницы бумаги")
class DividendTimingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 12);

    @Test
    @DisplayName("receivedDate — платёж известен → дата выплаты")
    void receivedDate_paymentDateKnown_returnsPaymentDate() {
        LocalDate result = DividendTiming.receivedDate(TODAY.minusMonths(2), TODAY.minusMonths(1));

        assertThat(result).isEqualTo(TODAY.minusMonths(1));
    }

    @Test
    @DisplayName("receivedDate — платёж не объявлен → дата отсечки")
    void receivedDate_noPaymentDate_fallsBackToRecordDate() {
        LocalDate result = DividendTiming.receivedDate(TODAY.minusMonths(2), null);

        assertThat(result).isEqualTo(TODAY.minusMonths(2));
    }

    @Test
    @DisplayName("isReceived — дата получения в прошлом → true")
    void isReceived_receivedDateInPast_returnsTrue() {
        assertThat(DividendTiming.isReceived(TODAY.minusDays(5), null, TODAY)).isTrue();
    }

    @Test
    @DisplayName("isReceived — отсечка прошла, но выплата ещё впереди → false (это ещё «предстоящий»)")
    void isReceived_recordDatePastButPaymentAhead_returnsFalse() {
        assertThat(DividendTiming.isReceived(TODAY.minusDays(2), TODAY.plusDays(3), TODAY)).isFalse();
    }

    @Test
    @DisplayName("isReceived — дата получения сегодня → false (сегодня относится к «предстоящим»)")
    void isReceived_receivedDateIsToday_returnsFalse() {
        assertThat(DividendTiming.isReceived(TODAY, null, TODAY)).isFalse();
    }

    @Test
    @DisplayName("isUpcoming — отсечка сегодня или впереди → true")
    void isUpcoming_recordDateTodayOrAhead_returnsTrue() {
        assertThat(DividendTiming.isUpcoming(TODAY, null, TODAY)).isTrue();
        assertThat(DividendTiming.isUpcoming(TODAY.plusDays(10), null, TODAY)).isTrue();
    }

    @Test
    @DisplayName("isUpcoming — отсечка прошла, выплата впереди → true")
    void isUpcoming_recordDatePastButPaymentAhead_returnsTrue() {
        assertThat(DividendTiming.isUpcoming(TODAY.minusDays(2), TODAY.plusDays(3), TODAY)).isTrue();
    }

    @Test
    @DisplayName("isUpcoming — обе даты в прошлом → false")
    void isUpcoming_bothDatesInPast_returnsFalse() {
        assertThat(DividendTiming.isUpcoming(TODAY.minusDays(10), TODAY.minusDays(2), TODAY)).isFalse();
    }

    @Test
    @DisplayName("upcomingEffectiveDate — отсечка ещё впереди → сортируется и показывается по отсечке")
    void upcomingEffectiveDate_recordDateAhead_returnsRecordDate() {
        LocalDate result = DividendTiming.upcomingEffectiveDate(TODAY.plusDays(10), null, TODAY);

        assertThat(result).isEqualTo(TODAY.plusDays(10));
    }

    @Test
    @DisplayName("upcomingEffectiveDate — отсечка прошла → сортируется и показывается по дате выплаты")
    void upcomingEffectiveDate_recordDatePassed_returnsPaymentDate() {
        LocalDate result = DividendTiming.upcomingEffectiveDate(TODAY.minusDays(2), TODAY.plusDays(3), TODAY);

        assertThat(result).isEqualTo(TODAY.plusDays(3));
    }

    @Test
    @DisplayName("isRecordDateAhead — отсечка сегодня или в будущем → true, иначе false")
    void isRecordDateAhead_todayOrFuture_true_pastFalse() {
        assertThat(DividendTiming.isRecordDateAhead(TODAY, TODAY)).isTrue();
        assertThat(DividendTiming.isRecordDateAhead(TODAY.plusDays(1), TODAY)).isTrue();
        assertThat(DividendTiming.isRecordDateAhead(TODAY.minusDays(1), TODAY)).isFalse();
    }
}
