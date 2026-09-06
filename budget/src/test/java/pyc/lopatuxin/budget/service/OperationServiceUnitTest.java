package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.response.OperationDto;
import pyc.lopatuxin.budget.dto.response.OperationsResponseDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;
import pyc.lopatuxin.budget.entity.enums.OperationKind;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationServiceUnitTest")
class OperationServiceUnitTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @InjectMocks
    private OperationService operationService;

    private UUID userId;
    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        startDate = LocalDate.of(2026, 9, 1);
        endDate = LocalDate.of(2026, 9, 30);
    }

    @Test
    @DisplayName("Должен объединить расходы и доходы в одну ленту")
    void shouldMergeExpensesAndIncomesIntoOneFeed() {
        Category category = Category.builder().id(UUID.randomUUID()).userId(userId).name("Продукты").emoji("🛒").build();
        Expense expense = buildExpense(category, LocalDate.of(2026, 9, 18), Instant.parse("2026-09-18T10:00:00Z"));
        Income income = buildIncome(LocalDate.of(2026, 9, 17), Instant.parse("2026-09-17T09:00:00Z"));

        when(expenseRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(List.of(expense));
        when(incomeRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(List.of(income));

        OperationsResponseDto result = operationService.getOperations(userId, 9, 2026);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems()).extracting(OperationDto::getKind)
                .containsExactlyInAnyOrder(OperationKind.EXPENSE, OperationKind.INCOME);
    }

    @Test
    @DisplayName("Должен отсортировать операции по дате по убыванию")
    void shouldSortOperationsByDateDescending() {
        Category category = Category.builder().id(UUID.randomUUID()).userId(userId).name("Продукты").emoji("🛒").build();
        Expense olderExpense = buildExpense(category, LocalDate.of(2026, 9, 5), Instant.parse("2026-09-05T10:00:00Z"));
        Expense newerExpense = buildExpense(category, LocalDate.of(2026, 9, 20), Instant.parse("2026-09-20T10:00:00Z"));

        when(expenseRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(List.of(olderExpense, newerExpense));
        when(incomeRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(Collections.emptyList());

        OperationsResponseDto result = operationService.getOperations(userId, 9, 2026);

        assertThat(result.getItems()).extracting(OperationDto::getDate)
                .containsExactly(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 5));
    }

    @Test
    @DisplayName("При равной дате должен отсортировать по createdAt по убыванию")
    void shouldSortBySameDateByCreatedAtDescending() {
        Category category = Category.builder().id(UUID.randomUUID()).userId(userId).name("Продукты").emoji("🛒").build();
        LocalDate sameDate = LocalDate.of(2026, 9, 18);
        Expense earlierCreated = buildExpense(category, sameDate, Instant.parse("2026-09-18T08:00:00Z"));
        Income laterCreated = buildIncome(sameDate, Instant.parse("2026-09-18T09:30:00Z"));

        when(expenseRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(List.of(earlierCreated));
        when(incomeRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(List.of(laterCreated));

        OperationsResponseDto result = operationService.getOperations(userId, 9, 2026);

        assertThat(result.getItems()).extracting(OperationDto::getKind)
                .containsExactly(OperationKind.INCOME, OperationKind.EXPENSE);
    }

    @Test
    @DisplayName("Репозиторий уже исключает трансферы — сервис не должен добавлять собственную фильтрацию")
    void shouldRelyOnRepositoryForTransferExclusion() {
        when(expenseRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(Collections.emptyList());
        when(incomeRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(Collections.emptyList());

        OperationsResponseDto result = operationService.getOperations(userId, 9, 2026);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    @DisplayName("Должен заполнить categoryEmoji и categoryName для расхода")
    void shouldPopulateCategoryFieldsForExpense() {
        Category category = Category.builder().id(UUID.randomUUID()).userId(userId).name("Продукты").emoji("🛒").build();
        Expense expense = buildExpense(category, LocalDate.of(2026, 9, 18), Instant.parse("2026-09-18T10:00:00Z"));

        when(expenseRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(List.of(expense));
        when(incomeRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(Collections.emptyList());

        OperationDto dto = operationService.getOperations(userId, 9, 2026).getItems().getFirst();

        assertThat(dto.getCategoryId()).isEqualTo(category.getId());
        assertThat(dto.getCategoryName()).isEqualTo("Продукты");
        assertThat(dto.getCategoryEmoji()).isEqualTo("🛒");
        assertThat(dto.getSource()).isNull();
        assertThat(dto.getSourceName()).isNull();
    }

    @Test
    @DisplayName("Должен заполнить sourceName для дохода")
    void shouldPopulateSourceNameForIncome() {
        Income income = buildIncome(LocalDate.of(2026, 9, 17), Instant.parse("2026-09-17T09:00:00Z"));

        when(expenseRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(Collections.emptyList());
        when(incomeRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(List.of(income));

        OperationDto dto = operationService.getOperations(userId, 9, 2026).getItems().getFirst();

        assertThat(dto.getSource()).isEqualTo(IncomeSource.FREELANCE);
        assertThat(dto.getSourceName()).isEqualTo("Фриланс");
        assertThat(dto.getCategoryId()).isNull();
        assertThat(dto.getCategoryName()).isNull();
    }

    @Test
    @DisplayName("total должен равняться размеру списка items")
    void shouldReturnTotalEqualToItemsSize() {
        Category category = Category.builder().id(UUID.randomUUID()).userId(userId).name("Продукты").build();
        List<Expense> expenses = List.of(
                buildExpense(category, LocalDate.of(2026, 9, 1), Instant.parse("2026-09-01T10:00:00Z")),
                buildExpense(category, LocalDate.of(2026, 9, 2), Instant.parse("2026-09-02T10:00:00Z")),
                buildExpense(category, LocalDate.of(2026, 9, 3), Instant.parse("2026-09-03T10:00:00Z"))
        );
        List<Income> incomes = List.of(
                buildIncome(LocalDate.of(2026, 9, 5), Instant.parse("2026-09-05T10:00:00Z"))
        );

        when(expenseRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(expenses);
        when(incomeRepository.findByUserIdAndDateBetweenAndIsTransferFalse(userId, startDate, endDate))
                .thenReturn(incomes);

        OperationsResponseDto result = operationService.getOperations(userId, 9, 2026);

        assertThat(result.getTotal()).isEqualTo(4);
        assertThat(result.getItems()).hasSize(4);
    }

    private Expense buildExpense(Category category, LocalDate date, Instant createdAt) {
        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("1000.00"))
                .date(date)
                .build();
        expense.setCreatedAt(createdAt);
        return expense;
    }

    private Income buildIncome(LocalDate date, Instant createdAt) {
        Income income = Income.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .source(IncomeSource.FREELANCE)
                .amount(new BigDecimal("5000.00"))
                .date(date)
                .build();
        income.setCreatedAt(createdAt);
        return income;
    }
}
