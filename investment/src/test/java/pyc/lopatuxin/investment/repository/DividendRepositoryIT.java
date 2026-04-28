package pyc.lopatuxin.investment.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pyc.lopatuxin.investment.AbstractIntegrationTest;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    @Test
    @DisplayName("Should save dividend and find by security ticker")
    void shouldSaveAndFindBySecurityTicker() {
        Security security = securityRepository.save(Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build());

        Dividend dividend = Dividend.builder()
                .security(security)
                .recordDate(LocalDate.of(2024, 6, 10))
                .paymentDate(LocalDate.of(2024, 7, 15))
                .amountPerShare(new BigDecimal("33.3000"))
                .currency("RUB")
                .status(DividendStatus.ANNOUNCED)
                .build();
        dividendRepository.save(dividend);

        List<Dividend> found = dividendRepository.findBySecurity_Ticker("SBER");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getAmountPerShare()).isEqualByComparingTo(new BigDecimal("33.3000"));
        assertThat(found.get(0).getStatus()).isEqualTo(DividendStatus.ANNOUNCED);
    }

    @Test
    @DisplayName("Should return empty list for unknown ticker")
    void shouldReturnEmptyForUnknownTicker() {
        List<Dividend> found = dividendRepository.findBySecurity_Ticker("UNKNOWN");
        assertThat(found).isEmpty();
    }
}
