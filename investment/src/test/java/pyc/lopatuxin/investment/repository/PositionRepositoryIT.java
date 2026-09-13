package pyc.lopatuxin.investment.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pyc.lopatuxin.investment.AbstractIntegrationTest;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionRepositoryIT extends AbstractIntegrationTest {

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
    @DisplayName("Should save position and find by userId and ticker")
    void shouldSaveAndFindByUserIdAndTicker() {
        Security security = securityRepository.save(Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build());

        UUID userId = UUID.randomUUID();
        Position position = Position.builder()
                .userId(userId)
                .security(security)
                .quantity(new BigDecimal("10.00000000"))
                .averagePrice(new BigDecimal("280.50"))
                .totalCost(new BigDecimal("2805.00"))
                .build();
        positionRepository.save(position);

        Optional<Position> found = positionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER");
        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualByComparingTo(new BigDecimal("10.00000000"));
    }

    @Test
    @DisplayName("Should throw DataIntegrityViolationException on duplicate (userId, security_ticker)")
    void shouldThrowOnDuplicateUserIdAndTicker() {
        Security security = securityRepository.save(Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build());

        UUID userId = UUID.randomUUID();
        Position first = Position.builder()
                .userId(userId)
                .security(security)
                .quantity(new BigDecimal("10.00000000"))
                .averagePrice(new BigDecimal("280.50"))
                .totalCost(new BigDecimal("2805.00"))
                .build();
        positionRepository.saveAndFlush(first);

        Position duplicate = Position.builder()
                .userId(userId)
                .security(security)
                .quantity(new BigDecimal("5.00000000"))
                .averagePrice(new BigDecimal("290.00"))
                .totalCost(new BigDecimal("1450.00"))
                .build();

        assertThatThrownBy(() -> positionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should find all positions by userId")
    void shouldFindByUserId() {
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
        positionRepository.saveAll(List.of(
                Position.builder()
                        .userId(userId)
                        .security(sber)
                        .quantity(new BigDecimal("10.00000000"))
                        .averagePrice(new BigDecimal("280.50"))
                        .totalCost(new BigDecimal("2805.00"))
                        .build(),
                Position.builder()
                        .userId(userId)
                        .security(gazp)
                        .quantity(new BigDecimal("5.00000000"))
                        .averagePrice(new BigDecimal("150.00"))
                        .totalCost(new BigDecimal("750.00"))
                        .build()
        ));

        List<Position> positions = positionRepository.findByUserId(userId);
        assertThat(positions).hasSize(2);
    }
}
