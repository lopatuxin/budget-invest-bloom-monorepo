package pyc.lopatuxin.investment.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pyc.lopatuxin.investment.AbstractIntegrationTest;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PriceHistoryRepositoryIT extends AbstractIntegrationTest {

    @BeforeEach
    void cleanUp() {
        dividendRepository.deleteAll();
        priceSnapshotRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        transactionRepository.deleteAll();
        positionRepository.deleteAll();
        securityRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save price history entries and find by ticker and date range")
    void shouldSaveAndFindByTickerAndDateRange() {
        securityRepository.save(Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build());

        LocalDate day1 = LocalDate.of(2024, 1, 10);
        LocalDate day2 = LocalDate.of(2024, 1, 11);
        LocalDate day3 = LocalDate.of(2024, 1, 12);

        priceHistoryRepository.saveAll(List.of(
                buildEntry("SBER", day1, "278.00", "280.50", "281.00", "277.00"),
                buildEntry("SBER", day2, "280.50", "282.00", "283.00", "279.00"),
                buildEntry("SBER", day3, "282.00", "279.50", "283.00", "278.00")
        ));

        List<PriceHistory> range = priceHistoryRepository
                .findByTickerAndTradeDateBetween("SBER", day1, day2);

        assertThat(range).hasSize(2);
        assertThat(range).extracting(PriceHistory::getTradeDate)
                .containsExactlyInAnyOrder(day1, day2);
    }

    @Test
    @DisplayName("findLastBeforeDateForTickers — одна строка на тикер: только последняя дата до окна")
    void findLastBeforeDateForTickers_returnsOnlyLastRowPerTickerBeforeDate() {
        securityRepository.save(Security.builder()
                .ticker("SBER").name("Сбербанк").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY).build());
        securityRepository.save(Security.builder()
                .ticker("LKOH").name("ЛУКОЙЛ").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY).build());

        LocalDate windowStart = LocalDate.of(2024, 6, 1);
        priceHistoryRepository.saveAll(List.of(
                buildEntry("SBER", LocalDate.of(2024, 5, 1), "260.00", "260.00", "260.00", "260.00"),
                buildEntry("SBER", LocalDate.of(2024, 5, 30), "270.00", "270.00", "270.00", "270.00"),
                // Inside/after the window — must not be picked as the "last before" row.
                buildEntry("SBER", LocalDate.of(2024, 6, 5), "290.00", "290.00", "290.00", "290.00"),
                buildEntry("LKOH", LocalDate.of(2024, 4, 1), "6000.00", "6000.00", "6000.00", "6000.00")
        ));

        List<PriceHistory> result = priceHistoryRepository
                .findLastBeforeDateForTickers(List.of("SBER", "LKOH"), windowStart);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PriceHistory::getTicker, PriceHistory::getTradeDate)
                .containsExactlyInAnyOrder(
                        tuple("SBER", LocalDate.of(2024, 5, 30)),
                        tuple("LKOH", LocalDate.of(2024, 4, 1)));
    }

    private PriceHistory buildEntry(String ticker, LocalDate date,
                                    String open, String close, String high, String low) {
        return PriceHistory.builder()
                .ticker(ticker)
                .tradeDate(date)
                .open(new BigDecimal(open))
                .close(new BigDecimal(close))
                .high(new BigDecimal(high))
                .low(new BigDecimal(low))
                .volume(1_000_000L)
                .build();
    }
}
