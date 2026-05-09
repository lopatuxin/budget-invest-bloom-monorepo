package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.request.InvestmentEntryRequestDto;
import pyc.lopatuxin.budget.dto.request.InvestmentEntryRequestDto.EntryType;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying that InvestmentEntryService sets isTransfer=true
 * on both Expense (BUY) and Income (SELL) entries.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvestmentEntryService — флаг isTransfer")
class InvestmentEntryServiceIsTransferTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private ExpenseService expenseService;

    @Mock
    private IncomeService incomeService;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @InjectMocks
    private InvestmentEntryService investmentEntryService;

    private UUID userId;
    private Category systemCategory;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        systemCategory = Category.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Инвестиции")
                .emoji("💎")
                .budget(BigDecimal.ZERO)
                .system(true)
                .build();
    }

    @Test
    @DisplayName("createBuyEntry: созданный Expense имеет isTransfer=true")
    void createBuyEntry_shouldSetIsTransferTrue_onExpense() {
        Expense savedExpense = Expense.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .category(systemCategory)
                .amount(new BigDecimal("10000.00"))
                .isTransfer(true)
                .build();

        when(categoryService.ensureSystemCategory(eq(userId), eq("Инвестиции"), eq("💎")))
                .thenReturn(systemCategory);
        when(expenseService.createInternal(
                eq(userId), eq(systemCategory), eq(new BigDecimal("10000.00")),
                any(LocalDate.class), eq(null), eq(true)))
                .thenReturn(savedExpense);

        InvestmentEntryRequestDto dto = InvestmentEntryRequestDto.builder()
                .type(EntryType.BUY)
                .amount(new BigDecimal("10000.00"))
                .executedAt(Instant.parse("2026-05-09T10:00:00Z"))
                .build();

        investmentEntryService.create(userId, dto);

        // Verify that createInternal was called with isTransfer=true
        ArgumentCaptor<Boolean> isTransferCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(expenseService).createInternal(
                eq(userId), eq(systemCategory), eq(new BigDecimal("10000.00")),
                any(LocalDate.class), eq(null), isTransferCaptor.capture());

        assertThat(isTransferCaptor.getValue()).isTrue();
    }

    @Test
    @DisplayName("createSellEntry: созданный Income имеет isTransfer=true")
    void createSellEntry_shouldSetIsTransferTrue_onIncome() {
        Income savedIncome = Income.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .source(IncomeSource.INVESTMENTS)
                .amount(new BigDecimal("15000.00"))
                .isTransfer(true)
                .build();

        when(incomeService.createInternal(
                eq(userId), eq(IncomeSource.INVESTMENTS), eq(new BigDecimal("15000.00")),
                any(LocalDate.class), eq("Продажа активов"), eq(true)))
                .thenReturn(savedIncome);

        InvestmentEntryRequestDto dto = InvestmentEntryRequestDto.builder()
                .type(EntryType.SELL)
                .amount(new BigDecimal("15000.00"))
                .executedAt(Instant.parse("2026-05-09T11:00:00Z"))
                .build();

        investmentEntryService.create(userId, dto);

        // Verify that createInternal was called with isTransfer=true
        ArgumentCaptor<Boolean> isTransferCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(incomeService).createInternal(
                eq(userId), eq(IncomeSource.INVESTMENTS), eq(new BigDecimal("15000.00")),
                any(LocalDate.class), eq("Продажа активов"), isTransferCaptor.capture());

        assertThat(isTransferCaptor.getValue()).isTrue();
    }

    @Test
    @DisplayName("createBuyEntry: дата исполнения корректно конвертируется в LocalDate (UTC)")
    void createBuyEntry_shouldConvertInstantToLocalDateCorrectly() {
        Expense savedExpense = Expense.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .category(systemCategory)
                .amount(new BigDecimal("5000.00"))
                .isTransfer(true)
                .build();

        when(categoryService.ensureSystemCategory(any(), any(), any())).thenReturn(systemCategory);
        when(expenseService.createInternal(any(), any(), any(), any(), any(), eq(true))).thenReturn(savedExpense);

        // 2026-05-09T23:30:00Z → LocalDate 2026-05-09 в UTC
        InvestmentEntryRequestDto dto = InvestmentEntryRequestDto.builder()
                .type(EntryType.BUY)
                .amount(new BigDecimal("5000.00"))
                .executedAt(Instant.parse("2026-05-09T23:30:00Z"))
                .build();

        investmentEntryService.create(userId, dto);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(expenseService).createInternal(
                any(), any(), any(), dateCaptor.capture(), any(), eq(true));

        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.of(2026, 5, 9));
    }

    @Test
    @DisplayName("createSellEntry: дата исполнения корректно конвертируется в LocalDate (UTC)")
    void createSellEntry_shouldConvertInstantToLocalDateCorrectly() {
        Income savedIncome = Income.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .source(IncomeSource.INVESTMENTS)
                .amount(new BigDecimal("5000.00"))
                .isTransfer(true)
                .build();

        when(incomeService.createInternal(any(), any(), any(), any(), any(), eq(true))).thenReturn(savedIncome);

        InvestmentEntryRequestDto dto = InvestmentEntryRequestDto.builder()
                .type(EntryType.SELL)
                .amount(new BigDecimal("5000.00"))
                .executedAt(Instant.parse("2026-03-15T00:00:00Z"))
                .build();

        investmentEntryService.create(userId, dto);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(incomeService).createInternal(
                any(), any(), any(), dateCaptor.capture(), any(), eq(true));

        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.of(2026, 3, 15));
    }
}
