package pyc.lopatuxin.investment.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pyc.lopatuxin.investment.AbstractIntegrationTest;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.DividendSource;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DividendRepositoryIT extends AbstractIntegrationTest {

    @BeforeEach
    void cleanUp() {
        dividendRepository.deleteAll();
        priceSnapshotRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        transactionRepository.deleteAll();
        positionRepository.deleteAll();
        securityRepository.deleteAll();
    }

    private Security saveSber() {
        return securityRepository.save(Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build());
    }

    @Test
    @DisplayName("Should save dividend and find by security ticker")
    void shouldSaveAndFindBySecurityTicker() {
        Security security = saveSber();

        Dividend dividend = Dividend.builder()
                .security(security)
                .recordDate(LocalDate.of(2024, 6, 10))
                .paymentDate(LocalDate.of(2024, 7, 15))
                .amountPerShare(new BigDecimal("33.3000"))
                .currency("RUB")
                .status(DividendStatus.ANNOUNCED)
                .source(DividendSource.TINVEST)
                .build();
        dividendRepository.save(dividend);

        List<Dividend> found = dividendRepository.findBySecurity_Ticker("SBER");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getAmountPerShare()).isEqualByComparingTo(new BigDecimal("33.3000"));
        assertThat(found.get(0).getStatus()).isEqualTo(DividendStatus.ANNOUNCED);
        assertThat(found.get(0).getSource()).isEqualTo(DividendSource.TINVEST);
    }

    @Test
    @DisplayName("Should return empty list for unknown ticker")
    void shouldReturnEmptyForUnknownTicker() {
        List<Dividend> found = dividendRepository.findBySecurity_Ticker("UNKNOWN");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByTickerInAndReceivedDateBetweenWithSecurity — отсечка сегодня без даты выплаты не входит в окно (только предстоящая)")
    void receivedWindow_excludesTodaysRecordDateWhenNoPaymentDate() {
        Security security = saveSber();

        Dividend today = Dividend.builder().security(security)
                .recordDate(LocalDate.now())
                .amountPerShare(new BigDecimal("34.84"))
                .currency("RUB").status(DividendStatus.ANNOUNCED).source(DividendSource.TINVEST).build();
        Dividend yesterday = Dividend.builder().security(security)
                .recordDate(LocalDate.now().minusDays(1))
                .amountPerShare(new BigDecimal("10.00"))
                .currency("RUB").status(DividendStatus.PAID).source(DividendSource.TINVEST).build();
        dividendRepository.saveAll(List.of(today, yesterday));

        List<Dividend> recent = dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(
                List.of("SBER"), LocalDate.now().minusYears(1), LocalDate.now());
        List<Dividend> upcoming = dividendRepository.findUpcomingByTickersWithSecurity(List.of("SBER"), LocalDate.now());

        assertThat(recent).extracting(Dividend::getRecordDate).containsExactly(LocalDate.now().minusDays(1));
        assertThat(upcoming).extracting(Dividend::getRecordDate).containsExactly(LocalDate.now());
    }

    @Test
    @DisplayName("findByTickerInAndReceivedDateBetweenWithSecurity — использует дату выплаты, если она есть, вместо даты отсечки")
    void receivedWindow_prefersPaymentDateOverRecordDate() {
        Security security = saveSber();

        // recordDate is 13 months ago (outside the window on its own), paymentDate is 1 month
        // ago (inside it) — the row must be included because "received" falls back to paymentDate.
        Dividend dividend = Dividend.builder().security(security)
                .recordDate(LocalDate.now().minusMonths(13))
                .paymentDate(LocalDate.now().minusMonths(1))
                .amountPerShare(new BigDecimal("10.00"))
                .currency("RUB").status(DividendStatus.PAID).source(DividendSource.TINVEST).build();
        dividendRepository.save(dividend);

        List<Dividend> recent = dividendRepository.findByTickerInAndReceivedDateBetweenWithSecurity(
                List.of("SBER"), LocalDate.now().minusMonths(12), LocalDate.now());

        assertThat(recent).hasSize(1);
    }

    @Test
    @DisplayName("findUpcomingByTickersWithSecurity — попадает запись с прошедшей отсечкой, но будущей выплатой")
    void upcoming_matchesByEitherDate() {
        Security security = saveSber();

        Dividend pastRecordFuturePayment = Dividend.builder().security(security)
                .recordDate(LocalDate.now().minusDays(2))
                .paymentDate(LocalDate.now().plusDays(3))
                .amountPerShare(new BigDecimal("10.00"))
                .currency("RUB").status(DividendStatus.ANNOUNCED).source(DividendSource.TINVEST).build();
        Dividend pastBoth = Dividend.builder().security(security)
                .recordDate(LocalDate.now().minusDays(30))
                .paymentDate(LocalDate.now().minusDays(20))
                .amountPerShare(new BigDecimal("5.00"))
                .currency("RUB").status(DividendStatus.PAID).source(DividendSource.TINVEST).build();
        dividendRepository.saveAll(List.of(pastRecordFuturePayment, pastBoth));

        List<Dividend> upcoming = dividendRepository.findUpcomingByTickersWithSecurity(List.of("SBER"), LocalDate.now());

        assertThat(upcoming).extracting(Dividend::getAmountPerShare)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("findByTickerInAndReceivedDateBeforeWithSecurity — не ограничен 12 месяцами, но исключает отсечку сегодня и CANCELLED")
    void receivedBeforeToday_hasNoTwelveMonthFloor_excludesTodayAndCancelled() {
        Security security = saveSber();

        Dividend old = Dividend.builder().security(security)
                .recordDate(LocalDate.now().minusYears(2))
                .amountPerShare(new BigDecimal("10.00"))
                .currency("RUB").status(DividendStatus.PAID).source(DividendSource.TINVEST).build();
        Dividend today = Dividend.builder().security(security)
                .recordDate(LocalDate.now())
                .amountPerShare(new BigDecimal("20.00"))
                .currency("RUB").status(DividendStatus.ANNOUNCED).source(DividendSource.TINVEST).build();
        Dividend cancelled = Dividend.builder().security(security)
                .recordDate(LocalDate.now().minusMonths(1))
                .amountPerShare(new BigDecimal("30.00"))
                .currency("RUB").status(DividendStatus.CANCELLED).source(DividendSource.TINVEST).build();
        dividendRepository.saveAll(List.of(old, today, cancelled));

        List<Dividend> received = dividendRepository.findByTickerInAndReceivedDateBeforeWithSecurity(
                List.of("SBER"), LocalDate.now());

        assertThat(received).extracting(Dividend::getRecordDate).containsExactly(LocalDate.now().minusYears(2));
    }

    @Test
    @DisplayName("findBySecurity_TickerAndRecordDate — находит существующую запись по ключу тикер+дата отсечки")
    void findBySecurityTickerAndRecordDate_findsExisting() {
        Security security = saveSber();
        Dividend dividend = Dividend.builder().security(security)
                .recordDate(LocalDate.of(2026, 7, 18))
                .amountPerShare(new BigDecimal("34.84"))
                .currency("RUB").status(DividendStatus.ANNOUNCED).source(DividendSource.TINVEST).build();
        dividendRepository.save(dividend);

        Optional<Dividend> found = dividendRepository.findBySecurity_TickerAndRecordDate("SBER", LocalDate.of(2026, 7, 18));

        assertThat(found).isPresent();
        assertThat(found.get().getAmountPerShare()).isEqualByComparingTo("34.84");
    }

    @Test
    @DisplayName("findBySecurity_TickerOrderByRecordDateDesc — сортирует по дате отсечки по убыванию")
    void findBySecurityTickerOrderByRecordDateDesc_sortsDescending() {
        Security security = saveSber();
        Dividend older = Dividend.builder().security(security)
                .recordDate(LocalDate.of(2023, 5, 15))
                .amountPerShare(new BigDecimal("18.70"))
                .currency("RUB").status(DividendStatus.PAID).source(DividendSource.MOEX).build();
        Dividend newer = Dividend.builder().security(security)
                .recordDate(LocalDate.of(2024, 5, 15))
                .amountPerShare(new BigDecimal("25.00"))
                .currency("RUB").status(DividendStatus.PAID).source(DividendSource.TINVEST).build();
        dividendRepository.saveAll(List.of(older, newer));

        List<Dividend> all = dividendRepository.findBySecurity_TickerOrderByRecordDateDesc("SBER");

        assertThat(all).extracting(Dividend::getRecordDate)
                .containsExactly(LocalDate.of(2024, 5, 15), LocalDate.of(2023, 5, 15));
    }
}
