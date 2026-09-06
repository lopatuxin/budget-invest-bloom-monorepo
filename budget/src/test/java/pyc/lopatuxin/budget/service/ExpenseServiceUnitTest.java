package pyc.lopatuxin.budget.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.request.CreateExpenseDto;
import pyc.lopatuxin.budget.dto.request.DeleteExpenseRequestDto;
import pyc.lopatuxin.budget.dto.response.ExpenseResponseDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.exception.BudgetConflictException;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExpenseServiceUnitTest")
class ExpenseServiceUnitTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ExpenseService expenseService;

    private UUID userId;
    private UUID categoryId;
    private Category category;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        category = Category.builder()
                .id(categoryId)
                .userId(userId)
                .name("Продукты")
                .budget(BigDecimal.ZERO)
                .build();
    }

    @Test
    @DisplayName("Должен создать расход с указанной датой и вернуть корректный ExpenseResponseDto")
    void shouldCreateExpenseWithExplicitDateAndReturnCorrectDto() {
        LocalDate explicitDate = LocalDate.of(2026, 3, 15);
        CreateExpenseDto dto = CreateExpenseDto.builder()
                .categoryId(categoryId)
                .amount(new BigDecimal("1500.00"))
                .description("Продукты в магазине")
                .date(explicitDate)
                .build();

        UUID expenseId = UUID.randomUUID();
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense saved = invocation.getArgument(0);
            saved.setId(expenseId);
            return saved;
        });

        ExpenseResponseDto result = expenseService.createExpense(userId, dto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(expenseId);
        assertThat(result.getCategoryId()).isEqualTo(categoryId);
        assertThat(result.getCategoryName()).isEqualTo("Продукты");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(result.getDescription()).isEqualTo("Продукты в магазине");
        assertThat(result.getDate()).isEqualTo(explicitDate);

        verify(categoryRepository).findByIdAndUserId(categoryId, userId);
        verify(expenseRepository).save(any(Expense.class));
    }

    @Test
    @DisplayName("Должен использовать текущую дату, если дата не указана в запросе")
    void shouldUseTodayDateWhenDateIsNull() {
        CreateExpenseDto dto = CreateExpenseDto.builder()
                .categoryId(categoryId)
                .amount(new BigDecimal("500.00"))
                .description(null)
                .date(null)
                .build();

        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ExpenseResponseDto result = expenseService.createExpense(userId, dto);

        assertThat(result.getDate()).isEqualTo(LocalDate.now());

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        assertThat(captor.getValue().getDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Должен выбросить EntityNotFoundException, если категория не найдена")
    void shouldThrowEntityNotFoundExceptionWhenCategoryNotFound() {
        UUID nonExistentCategoryId = UUID.randomUUID();
        CreateExpenseDto dto = CreateExpenseDto.builder()
                .categoryId(nonExistentCategoryId)
                .amount(new BigDecimal("100.00"))
                .build();

        when(categoryRepository.findByIdAndUserId(nonExistentCategoryId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.createExpense(userId, dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Категория не найдена");
    }

    @Test
    @DisplayName("Должен корректно сохранить expense с userId и category из аргументов")
    void shouldSaveExpenseWithCorrectUserIdAndCategory() {
        CreateExpenseDto dto = CreateExpenseDto.builder()
                .categoryId(categoryId)
                .amount(new BigDecimal("2500.50"))
                .description("Описание расхода")
                .date(LocalDate.of(2026, 4, 1))
                .build();

        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        expenseService.createExpense(userId, dto);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());

        Expense savedExpense = captor.getValue();
        assertThat(savedExpense.getUserId()).isEqualTo(userId);
        assertThat(savedExpense.getCategory()).isEqualTo(category);
        assertThat(savedExpense.getAmount()).isEqualByComparingTo(new BigDecimal("2500.50"));
        assertThat(savedExpense.getDescription()).isEqualTo("Описание расхода");
        assertThat(savedExpense.getDate()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    @DisplayName("Должен вернуть ExpenseResponseDto без описания, если оно не указано")
    void shouldReturnDtoWithNullDescriptionWhenNotProvided() {
        CreateExpenseDto dto = CreateExpenseDto.builder()
                .categoryId(categoryId)
                .amount(new BigDecimal("300.00"))
                .date(LocalDate.of(2026, 1, 10))
                .build();

        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ExpenseResponseDto result = expenseService.createExpense(userId, dto);

        assertThat(result.getDescription()).isNull();
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    // --- deleteExpense ---

    @Test
    @DisplayName("deleteExpense: должен удалить обычный расход пользователя")
    void deleteExpense_shouldDeleteExpense_whenOwnedByUserAndNotTransfer() {
        UUID expenseId = UUID.randomUUID();
        Expense expense = Expense.builder()
                .id(expenseId)
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("100.00"))
                .isTransfer(false)
                .build();

        DeleteExpenseRequestDto dto = DeleteExpenseRequestDto.builder().expenseId(expenseId).build();
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));

        expenseService.deleteExpense(userId, dto);

        verify(expenseRepository).delete(expense);
    }

    @Test
    @DisplayName("deleteExpense: должен бросить EntityNotFoundException, если расход не найден")
    void deleteExpense_shouldThrowEntityNotFoundException_whenExpenseNotFound() {
        UUID expenseId = UUID.randomUUID();
        DeleteExpenseRequestDto dto = DeleteExpenseRequestDto.builder().expenseId(expenseId).build();
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.deleteExpense(userId, dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Расход не найден");

        verify(expenseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteExpense: должен бросить EntityNotFoundException, если расход принадлежит другому пользователю")
    void deleteExpense_shouldThrowEntityNotFoundException_whenExpenseBelongsToAnotherUser() {
        UUID expenseId = UUID.randomUUID();
        Expense expense = Expense.builder()
                .id(expenseId)
                .userId(UUID.randomUUID())
                .category(category)
                .amount(new BigDecimal("100.00"))
                .build();

        DeleteExpenseRequestDto dto = DeleteExpenseRequestDto.builder().expenseId(expenseId).build();
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));

        assertThatThrownBy(() -> expenseService.deleteExpense(userId, dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Расход не найден");

        verify(expenseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteExpense: должен бросить BudgetConflictException для записи-перевода (isTransfer=true)")
    void deleteExpense_shouldThrowBudgetConflictException_whenExpenseIsTransfer() {
        UUID expenseId = UUID.randomUUID();
        Expense transferExpense = Expense.builder()
                .id(expenseId)
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("50000.00"))
                .isTransfer(true)
                .build();

        DeleteExpenseRequestDto dto = DeleteExpenseRequestDto.builder().expenseId(expenseId).build();
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(transferExpense));

        assertThatThrownBy(() -> expenseService.deleteExpense(userId, dto))
                .isInstanceOf(BudgetConflictException.class);

        verify(expenseRepository, never()).delete(any());
    }
}
