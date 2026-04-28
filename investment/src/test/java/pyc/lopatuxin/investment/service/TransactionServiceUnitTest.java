package pyc.lopatuxin.investment.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.dto.request.CreateTransactionDto;
import pyc.lopatuxin.investment.dto.response.TransactionResponseDto;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
import pyc.lopatuxin.investment.mapper.TransactionMapper;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;
import pyc.lopatuxin.investment.repository.TransactionRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionServiceUnitTest")
class TransactionServiceUnitTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private TransactionService transactionService;

    private UUID userId;
    private Security security;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        security = Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("BUY новый тикер — security создаётся on-the-fly, позиция сохраняется с правильным avgPrice")
    void shouldCreateSecurityOnTheFlyAndSavePositionOnBuy() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .ticker("SBER")
                .type(TransactionType.BUY)
                .securityType(SecurityType.STOCK)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("250.00"))
                .executedAt(Instant.now())
                .build();

        // marketDataService.ensureSecurity is called in create(); recalculatePosition uses securityRepository directly
        when(marketDataService.ensureSecurity("SBER", SecurityType.STOCK)).thenReturn(security);
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(security));

        Transaction savedTx = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(security)
                .type(TransactionType.BUY)
                .quantity(dto.getQuantity())
                .price(dto.getPrice())
                .executedAt(dto.getExecutedAt())
                .build();
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTx);

        Transaction buyTx = Transaction.builder()
                .userId(userId)
                .security(security)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("250.00"))
                .executedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        when(transactionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER"))
                .thenReturn(List.of(buyTx));
        when(positionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER"))
                .thenReturn(Optional.empty());
        when(transactionMapper.toDto(any())).thenReturn(new TransactionResponseDto());

        transactionService.create(userId, dto);

        ArgumentCaptor<Position> posCaptor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository).save(posCaptor.capture());
        Position savedPos = posCaptor.getValue();
        assertThat(savedPos.getQuantity()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(savedPos.getAveragePrice()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    @DisplayName("Два BUY — средневзвешенная цена (10×250 + 5×280)/15 = 260")
    void shouldCalculateWeightedAveragePriceForTwoBuys() {
        Instant t1 = Instant.ofEpochSecond(1000);
        Instant t2 = Instant.ofEpochSecond(2000);

        Transaction buy1 = Transaction.builder()
                .userId(userId).security(security)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("250.00"))
                .executedAt(t1).createdAt(t1).build();

        Transaction buy2 = Transaction.builder()
                .userId(userId).security(security)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("5"))
                .price(new BigDecimal("280.00"))
                .executedAt(t2).createdAt(t2).build();

        CreateTransactionDto dto = CreateTransactionDto.builder()
                .ticker("SBER").type(TransactionType.BUY).securityType(SecurityType.STOCK)
                .quantity(new BigDecimal("5")).price(new BigDecimal("280.00"))
                .executedAt(t2).build();

        when(marketDataService.ensureSecurity("SBER", SecurityType.STOCK)).thenReturn(security);
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(security));
        when(transactionRepository.save(any())).thenReturn(buy2);
        when(transactionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER"))
                .thenReturn(List.of(buy1, buy2));
        when(positionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER"))
                .thenReturn(Optional.empty());
        when(transactionMapper.toDto(any())).thenReturn(new TransactionResponseDto());

        transactionService.create(userId, dto);

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo(new BigDecimal("15"));
        assertThat(captor.getValue().getAveragePrice()).isEqualByComparingTo(new BigDecimal("260.00"));
    }

    @Test
    @DisplayName("BUY + SELL частичная — qty уменьшается, totalCost пропорционально")
    void shouldReduceQtyAndTotalCostOnPartialSell() {
        Instant t1 = Instant.ofEpochSecond(1000);
        Instant t2 = Instant.ofEpochSecond(2000);

        Transaction buy = Transaction.builder()
                .userId(userId).security(security)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("250.00"))
                .executedAt(t1).createdAt(t1).build();

        Transaction sell = Transaction.builder()
                .userId(userId).security(security)
                .type(TransactionType.SELL)
                .quantity(new BigDecimal("4"))
                .price(new BigDecimal("300.00"))
                .executedAt(t2).createdAt(t2).build();

        CreateTransactionDto dto = CreateTransactionDto.builder()
                .ticker("SBER").type(TransactionType.SELL).securityType(SecurityType.STOCK)
                .quantity(new BigDecimal("4")).price(new BigDecimal("300.00"))
                .executedAt(t2).build();

        when(marketDataService.ensureSecurity("SBER", SecurityType.STOCK)).thenReturn(security);
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(security));
        when(transactionRepository.save(any())).thenReturn(sell);
        when(transactionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER"))
                .thenReturn(List.of(buy, sell));
        when(positionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER"))
                .thenReturn(Optional.empty());
        when(transactionMapper.toDto(any())).thenReturn(new TransactionResponseDto());

        transactionService.create(userId, dto);

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo(new BigDecimal("6"));
        // totalCost = 2500 - (250 * 4) = 1500
        assertThat(captor.getValue().getTotalCost()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    @DisplayName("BUY + SELL полная (qty=0) — позиция удаляется")
    void shouldDeletePositionWhenQtyBecomesZero() {
        Instant t1 = Instant.ofEpochSecond(1000);
        Instant t2 = Instant.ofEpochSecond(2000);

        Transaction buy = Transaction.builder()
                .userId(userId).security(security)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("250.00"))
                .executedAt(t1).createdAt(t1).build();

        Transaction sell = Transaction.builder()
                .userId(userId).security(security)
                .type(TransactionType.SELL)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("300.00"))
                .executedAt(t2).createdAt(t2).build();

        Position existingPosition = Position.builder()
                .id(UUID.randomUUID()).userId(userId).security(security)
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("250.00"))
                .totalCost(new BigDecimal("2500.00"))
                .build();

        CreateTransactionDto dto = CreateTransactionDto.builder()
                .ticker("SBER").type(TransactionType.SELL).securityType(SecurityType.STOCK)
                .quantity(new BigDecimal("10")).price(new BigDecimal("300.00"))
                .executedAt(t2).build();

        when(marketDataService.ensureSecurity("SBER", SecurityType.STOCK)).thenReturn(security);
        when(transactionRepository.save(any())).thenReturn(sell);
        when(transactionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER"))
                .thenReturn(List.of(buy, sell));
        when(positionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER"))
                .thenReturn(Optional.of(existingPosition));
        when(transactionMapper.toDto(any())).thenReturn(new TransactionResponseDto());

        transactionService.create(userId, dto);

        verify(positionRepository).delete(existingPosition);
        verify(positionRepository, never()).save(any());
    }

    @Test
    @DisplayName("SELL без позиции (нет предшествующих BUY) — IllegalStateException")
    void shouldThrowWhenSellWithoutPriorBuy() {
        Instant t1 = Instant.ofEpochSecond(1000);

        Transaction sell = Transaction.builder()
                .userId(userId).security(security)
                .type(TransactionType.SELL)
                .quantity(new BigDecimal("5"))
                .price(new BigDecimal("300.00"))
                .executedAt(t1).createdAt(t1).build();

        CreateTransactionDto dto = CreateTransactionDto.builder()
                .ticker("SBER").type(TransactionType.SELL).securityType(SecurityType.STOCK)
                .quantity(new BigDecimal("5")).price(new BigDecimal("300.00"))
                .executedAt(t1).build();

        when(marketDataService.ensureSecurity("SBER", SecurityType.STOCK)).thenReturn(security);
        when(transactionRepository.save(any())).thenReturn(sell);
        when(transactionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER"))
                .thenReturn(List.of(sell));

        assertThatThrownBy(() -> transactionService.create(userId, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient shares for SELL");
    }

    @Test
    @DisplayName("delete() чужой транзакции — EntityNotFoundException")
    void shouldThrowWhenDeletingAnotherUserTransaction() {
        UUID otherId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        Transaction tx = Transaction.builder()
                .id(txId)
                .userId(otherId)
                .security(security)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("250.00"))
                .executedAt(Instant.now())
                .build();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> transactionService.delete(userId, txId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    @DisplayName("delete() — после удаления позиция пересчитывается")
    void shouldRecalculatePositionAfterDelete() {
        UUID txId = UUID.randomUUID();
        Instant t1 = Instant.ofEpochSecond(1000);
        Instant t2 = Instant.ofEpochSecond(2000);

        Transaction tx1 = Transaction.builder()
                .id(txId).userId(userId).security(security)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("10")).price(new BigDecimal("250.00"))
                .executedAt(t1).createdAt(t1).build();

        Transaction tx2 = Transaction.builder()
                .id(UUID.randomUUID()).userId(userId).security(security)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("5")).price(new BigDecimal("200.00"))
                .executedAt(t2).createdAt(t2).build();

        // After deleting tx1, only tx2 remains
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx1));
        when(transactionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER"))
                .thenReturn(List.of(tx2));
        when(positionRepository.findByUserIdAndSecurity_Ticker(userId, "SBER"))
                .thenReturn(Optional.empty());
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(security));

        transactionService.delete(userId, txId);

        verify(transactionRepository).delete(tx1);

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(captor.getValue().getAveragePrice()).isEqualByComparingTo(new BigDecimal("200.00"));
    }
}
