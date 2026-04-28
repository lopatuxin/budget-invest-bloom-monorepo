package pyc.lopatuxin.investment.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pyc.lopatuxin.investment.AbstractIntegrationTest;
import pyc.lopatuxin.investment.entity.PriceSnapshot;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PriceSnapshotRepositoryIT extends AbstractIntegrationTest {

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
    @DisplayName("Should save price snapshot and find by ticker")
    void shouldSaveAndFindById() {
        securityRepository.save(Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build());

        PriceSnapshot snapshot = PriceSnapshot.builder()
                .ticker("SBER")
                .lastPrice(new BigDecimal("283.50"))
                .previousClose(new BigDecimal("280.00"))
                .fetchedAt(Instant.now())
                .build();
        priceSnapshotRepository.save(snapshot);

        Optional<PriceSnapshot> found = priceSnapshotRepository.findById("SBER");
        assertThat(found).isPresent();
        assertThat(found.get().getLastPrice()).isEqualByComparingTo(new BigDecimal("283.50"));
        assertThat(found.get().getPreviousClose()).isEqualByComparingTo(new BigDecimal("280.00"));
    }
}
