package pyc.lopatuxin.investment.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.transaction.PlatformTransactionManager;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
import pyc.lopatuxin.investment.repository.TransactionRepository;
import pyc.lopatuxin.shared.port.EntryType;
import pyc.lopatuxin.shared.port.InvestmentBudgetSync;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvestmentBudgetMigrationRunnerTest")
class InvestmentBudgetMigrationRunnerTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private InvestmentBudgetSync investmentBudgetSync;

    @Mock
    private PlatformTransactionManager investmentTransactionManager;

    @Mock
    private ApplicationArguments appArgs;

    @InjectMocks
    private InvestmentBudgetMigrationRunner runner;

    private Security security;

    @BeforeEach
    void setUp() {
        security = Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("run(): все транзакции с budgetEntryId != null — runner ничего не делает")
    void run_shouldDoNothing_whenNoOrphans() throws Exception {
        when(transactionRepository.findAllByBudgetEntryIdIsNull()).thenReturn(List.of());

        runner.run(appArgs);

        verify(investmentBudgetSync, never()).createEntry(any(), any(), any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("run(): для каждой orphan-транзакции вызывается investmentBudgetSync.createEntry и сохраняется entryId")
    void run_shouldSyncOrphans_andSaveBudgetEntryId() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant executedAt = Instant.ofEpochSecond(1000);
        UUID budgetEntryId = UUID.randomUUID();

        Transaction orphan = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .security(security)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("10"))
                .price(new BigDecimal("250.00"))
                .executedAt(executedAt)
                .budgetEntryId(null)
                .build();

        when(transactionRepository.findAllByBudgetEntryIdIsNull()).thenReturn(List.of(orphan));
        when(investmentBudgetSync.createEntry(
                eq(userId), eq(EntryType.BUY), eq(new BigDecimal("2500.00")), eq(executedAt)))
                .thenReturn(budgetEntryId);
        when(transactionRepository.save(any())).thenReturn(orphan);

        runner.run(appArgs);

        verify(investmentBudgetSync).createEntry(userId, EntryType.BUY, new BigDecimal("2500.00"), executedAt);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getBudgetEntryId()).isEqualTo(budgetEntryId);
    }

    @Test
    @DisplayName("run(): если HTTP-вызов для одной orphan падает — остальные продолжают обрабатываться")
    void run_shouldContinueForOtherOrphans_whenOneOrphanFails() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant executedAt = Instant.ofEpochSecond(1000);
        UUID budgetEntryId2 = UUID.randomUUID();

        Transaction orphan1 = Transaction.builder()
                .id(UUID.randomUUID()).userId(userId).security(security)
                .type(TransactionType.BUY)
                .quantity(new BigDecimal("5")).price(new BigDecimal("100.00"))
                .executedAt(executedAt).budgetEntryId(null).build();

        Transaction orphan2 = Transaction.builder()
                .id(UUID.randomUUID()).userId(userId).security(security)
                .type(TransactionType.SELL)
                .quantity(new BigDecimal("3")).price(new BigDecimal("120.00"))
                .executedAt(executedAt).budgetEntryId(null).build();

        when(transactionRepository.findAllByBudgetEntryIdIsNull()).thenReturn(List.of(orphan1, orphan2));

        // Первая orphan — падает
        when(investmentBudgetSync.createEntry(
                eq(userId), eq(EntryType.BUY), eq(new BigDecimal("500.00")), eq(executedAt)))
                .thenThrow(new RuntimeException("503 Budget unavailable"));

        // Вторая orphan — успешно
        when(investmentBudgetSync.createEntry(
                eq(userId), eq(EntryType.SELL), eq(new BigDecimal("360.00")), eq(executedAt)))
                .thenReturn(budgetEntryId2);
        when(transactionRepository.save(any())).thenReturn(orphan2);

        runner.run(appArgs);

        // Оба вызова были сделаны
        verify(investmentBudgetSync, times(2)).createEntry(any(), any(), any(), any());
        // Сохранили только вторую (успешную)
        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("run(): повторный запуск после успешной миграции — 0 вызовов investmentBudgetSync")
    void run_shouldCallBudgetClientZeroTimes_afterSuccessfulMigration() throws Exception {
        // После миграции все транзакции имеют budgetEntryId — список orphan пустой
        when(transactionRepository.findAllByBudgetEntryIdIsNull()).thenReturn(List.of());

        runner.run(appArgs);
        runner.run(appArgs);

        verify(investmentBudgetSync, never()).createEntry(any(), any(), any(), any());
    }
}
