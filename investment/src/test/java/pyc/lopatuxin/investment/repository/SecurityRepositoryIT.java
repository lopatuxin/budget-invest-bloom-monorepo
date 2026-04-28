package pyc.lopatuxin.investment.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pyc.lopatuxin.investment.AbstractIntegrationTest;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityRepositoryIT extends AbstractIntegrationTest {

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
    @DisplayName("Should save and find security by ticker")
    void shouldSaveAndFindById() {
        Security security = Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .currency("RUB")
                .build();

        securityRepository.save(security);

        Optional<Security> found = securityRepository.findById("SBER");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Сбербанк");
        assertThat(found.get().getType()).isEqualTo(SecurityType.STOCK);
    }

    @Test
    @DisplayName("Should find securities by type")
    void shouldFindByType() {
        Security stock = Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build();
        Security bond = Security.builder()
                .ticker("OFZ26224")
                .name("ОФЗ 26224")
                .type(SecurityType.BOND)
                .historyStatus(HistoryStatus.PENDING)
                .build();
        securityRepository.saveAll(List.of(stock, bond));

        List<Security> stocks = securityRepository.findByType(SecurityType.STOCK);
        assertThat(stocks).hasSize(1);
        assertThat(stocks.get(0).getTicker()).isEqualTo("SBER");

        List<Security> bonds = securityRepository.findByType(SecurityType.BOND);
        assertThat(bonds).hasSize(1);
        assertThat(bonds.get(0).getTicker()).isEqualTo("OFZ26224");
    }

    @Test
    @DisplayName("Should find securities by history status")
    void shouldFindByHistoryStatus() {
        Security pending = Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build();
        Security ready = Security.builder()
                .ticker("GAZP")
                .name("Газпром")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY)
                .build();
        securityRepository.saveAll(List.of(pending, ready));

        List<Security> pendingList = securityRepository.findAllByHistoryStatus(HistoryStatus.PENDING);
        assertThat(pendingList).hasSize(1);
        assertThat(pendingList.get(0).getTicker()).isEqualTo("SBER");
    }
}
