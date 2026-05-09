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
import pyc.lopatuxin.budget.dto.response.InvestmentEntryResponseDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvestmentEntryServiceUnitTest")
class InvestmentEntryServiceUnitTest {

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
    @DisplayName("create(BUY): создаёт Expense в системной категории «Инвестиции», возвращает entryId")
    void create_buy_shouldCreateExpenseInSystemCategoryAndReturnEntryId() {
        UUID expenseId = UUID.randomUUID();
        Expense savedExpense = Expense.builder()
                .id(expenseId)
                .userId(userId)
                .category(systemCategory)
                .amount(new BigDecimal("10000.00"))
                .build();

        InvestmentEntryRequestDto dto = InvestmentEntryRequestDto.builder()
                .type(EntryType.BUY)
                .amount(new BigDecimal("10000.00"))
                .executedAt(Instant.parse("2026-05-09T10:00:00Z"))
                .build();

        when(categoryService.ensureSystemCategory(eq(userId), eq("Инвестиции"), eq("💎")))
                .thenReturn(systemCategory);
        when(expenseService.createInternal(any(), any(), any(), any(), any()))
                .thenReturn(savedExpense);

        InvestmentEntryResponseDto result = investmentEntryService.create(userId, dto);

        assertThat(result).isNotNull();
        assertThat(result.getEntryId()).isEqualTo(expenseId);
        verify(categoryService).ensureSystemCategory(userId, "Инвестиции", "💎");
        verify(expenseService).createInternal(
                eq(userId), eq(systemCategory), eq(new BigDecimal("10000.00")), any(), eq(null));
    }

    @Test
    @DisplayName("create(SELL): создаёт Income с source=INVESTMENTS, description=\"Продажа активов\"")
    void create_sell_shouldCreateIncomeWithInvestmentsSource() {
        UUID incomeId = UUID.randomUUID();
        Income savedIncome = Income.builder()
                .id(incomeId)
                .userId(userId)
                .source(IncomeSource.INVESTMENTS)
                .amount(new BigDecimal("15000.00"))
                .description("Продажа активов")
                .build();

        InvestmentEntryRequestDto dto = InvestmentEntryRequestDto.builder()
                .type(EntryType.SELL)
                .amount(new BigDecimal("15000.00"))
                .executedAt(Instant.parse("2026-05-09T11:00:00Z"))
                .build();

        when(incomeService.createInternal(any(), any(), any(), any(), any()))
                .thenReturn(savedIncome);

        InvestmentEntryResponseDto result = investmentEntryService.create(userId, dto);

        assertThat(result).isNotNull();
        assertThat(result.getEntryId()).isEqualTo(incomeId);

        ArgumentCaptor<IncomeSource> sourceCaptor = ArgumentCaptor.forClass(IncomeSource.class);
        ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);
        verify(incomeService).createInternal(
                eq(userId), sourceCaptor.capture(), eq(new BigDecimal("15000.00")), any(), descCaptor.capture());
        assertThat(sourceCaptor.getValue()).isEqualTo(IncomeSource.INVESTMENTS);
        assertThat(descCaptor.getValue()).isEqualTo("Продажа активов");

        // BUY-ветка не вызывается
        verify(categoryService, never()).ensureSystemCategory(any(), any(), any());
        verify(expenseService, never()).createInternal(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("create() повторно — переиспользует существующую системную категорию (categoryService вызывается дважды)")
    void create_buy_calledTwice_shouldReuseExistingSystemCategory() {
        Expense expense1 = Expense.builder().id(UUID.randomUUID()).userId(userId).category(systemCategory).amount(new BigDecimal("1000.00")).build();
        Expense expense2 = Expense.builder().id(UUID.randomUUID()).userId(userId).category(systemCategory).amount(new BigDecimal("2000.00")).build();

        InvestmentEntryRequestDto dto = InvestmentEntryRequestDto.builder()
                .type(EntryType.BUY)
                .amount(new BigDecimal("1000.00"))
                .executedAt(Instant.parse("2026-05-09T10:00:00Z"))
                .build();

        when(categoryService.ensureSystemCategory(userId, "Инвестиции", "💎"))
                .thenReturn(systemCategory);
        when(expenseService.createInternal(any(), any(), any(), any(), any()))
                .thenReturn(expense1)
                .thenReturn(expense2);

        investmentEntryService.create(userId, dto);
        investmentEntryService.create(userId, dto);

        // Оба вызова делегируют ensureSystemCategory — логика идемпотентности в CategoryService
        verify(categoryService, org.mockito.Mockito.times(2))
                .ensureSystemCategory(userId, "Инвестиции", "💎");
    }

    @Test
    @DisplayName("delete(entryId, BUY): удаляет Expense")
    void delete_buy_shouldDeleteExpense() {
        UUID expenseId = UUID.randomUUID();
        Expense expense = Expense.builder()
                .id(expenseId)
                .userId(userId)
                .category(systemCategory)
                .amount(new BigDecimal("5000.00"))
                .build();

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));

        investmentEntryService.delete(userId, expenseId, EntryType.BUY);

        verify(expenseRepository).delete(expense);
        verify(incomeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete(entryId, SELL): удаляет Income")
    void delete_sell_shouldDeleteIncome() {
        UUID incomeId = UUID.randomUUID();
        Income income = Income.builder()
                .id(incomeId)
                .userId(userId)
                .source(IncomeSource.INVESTMENTS)
                .amount(new BigDecimal("8000.00"))
                .build();

        when(incomeRepository.findById(incomeId)).thenReturn(Optional.of(income));

        investmentEntryService.delete(userId, incomeId, EntryType.SELL);

        verify(incomeRepository).delete(income);
        verify(expenseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete(несуществующий entryId, BUY): idempotent, не бросает исключение")
    void delete_buy_nonExistentId_shouldBeIdempotent() {
        UUID nonExistentId = UUID.randomUUID();
        when(expenseRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Не бросает исключение
        investmentEntryService.delete(userId, nonExistentId, EntryType.BUY);

        verify(expenseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete(несуществующий entryId, SELL): idempotent, не бросает исключение")
    void delete_sell_nonExistentId_shouldBeIdempotent() {
        UUID nonExistentId = UUID.randomUUID();
        when(incomeRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        investmentEntryService.delete(userId, nonExistentId, EntryType.SELL);

        verify(incomeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete(BUY): чужой expense — не удаляет")
    void delete_buy_expenseBelongsToAnotherUser_shouldNotDelete() {
        UUID otherUserId = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();
        Expense expense = Expense.builder()
                .id(expenseId)
                .userId(otherUserId)   // принадлежит другому пользователю
                .category(systemCategory)
                .amount(new BigDecimal("3000.00"))
                .build();

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));

        investmentEntryService.delete(userId, expenseId, EntryType.BUY);

        verify(expenseRepository, never()).delete(any());
    }
}
