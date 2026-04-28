package pyc.lopatuxin.investment.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pyc.lopatuxin.investment.AbstractIntegrationTest;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.entity.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionRepositoryIT extends AbstractIntegrationTest {

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
    @DisplayName("Should save transaction and find by userId")
    void shouldSaveAndFindByUserId() {
        Security security = securityRepository.save(Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build());

        UUID userId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .security(security)
                .quantity(new BigDecimal("10.00000000"))
                .price(new BigDecimal("280.50"))
                .executedAt(Instant.now())
                .type(TransactionType.BUY)
                .build();
        transactionRepository.save(transaction);

        List<Transaction> found = transactionRepository.findByUserId(userId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getType()).isEqualTo(TransactionType.BUY);
    }

    @Test
    @DisplayName("Should find transactions by userId and security ticker")
    void shouldFindByUserIdAndSecurityTicker() {
        Security sber = securityRepository.save(Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build());
        Security gazp = securityRepository.save(Security.builder()
                .ticker("GAZP")
                .name("Газпром")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build());

        UUID userId = UUID.randomUUID();
        transactionRepository.saveAll(List.of(
                Transaction.builder()
                        .userId(userId)
                        .security(sber)
                        .quantity(new BigDecimal("5.00000000"))
                        .price(new BigDecimal("280.00"))
                        .executedAt(Instant.now())
                        .type(TransactionType.BUY)
                        .build(),
                Transaction.builder()
                        .userId(userId)
                        .security(gazp)
                        .quantity(new BigDecimal("3.00000000"))
                        .price(new BigDecimal("150.00"))
                        .executedAt(Instant.now())
                        .type(TransactionType.BUY)
                        .build()
        ));

        List<Transaction> sberTx = transactionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER");
        assertThat(sberTx).hasSize(1);
        assertThat(sberTx.get(0).getSecurity().getTicker()).isEqualTo("SBER");
    }
}
