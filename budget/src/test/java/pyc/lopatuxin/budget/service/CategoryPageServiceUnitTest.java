package pyc.lopatuxin.budget.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.common.NormComparisonDto;
import pyc.lopatuxin.budget.dto.request.CategoryPageRequestDto;
import pyc.lopatuxin.budget.dto.response.CategoryMonthAmountDto;
import pyc.lopatuxin.budget.dto.response.CategoryPageResponseDto;
import pyc.lopatuxin.budget.dto.response.OperationDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.entity.enums.NormStatus;
import pyc.lopatuxin.budget.mapper.ExpenseMapper;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.service.NormCalculationService.MonthlyAggregateRow;
import pyc.lopatuxin.budget.service.NormWindowLoader.NormsContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryPageServiceUnitTest")
class CategoryPageServiceUnitTest {

    private static final NormsContext EMPTY_NORMS_CONTEXT =
            new NormsContext(List.of(), List.of(), List.of(), Map.of(), Map.of());

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private NormWindowLoader normWindowLoader;

    @Mock
    private NormCalculationService normCalculationService;

    @Mock
    private ExpenseMapper expenseMapper;

    @InjectMocks
    private CategoryPageService categoryPageService;

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
                .emoji("🛒")
                .budget(BigDecimal.ZERO)
                .build();

        lenient().when(categoryRepository.findByNameAndUserId("Продукты", userId)).thenReturn(Optional.of(category));
        lenient().when(expenseRepository.findMonthlyNonTransferExpenseByCategoryAndDateBetween(any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndDateBetween(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(expenseRepository.findByUserIdAndCategoryIdAndDateBetweenOrderByDateDesc(any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(normWindowLoader.loadForMonth(any(), any(), anyInt())).thenReturn(EMPTY_NORMS_CONTEXT);
        lenient().when(normWindowLoader.categoryDataMonths(any(), any())).thenReturn(List.of());
        lenient().when(normCalculationService.calculateNorm(any(), any(), anyInt()))
                .thenReturn(NormComparisonDto.builder().status(NormStatus.NO_HISTORY).build());
    }

    private CategoryPageRequestDto request(int month, int year) {
        return CategoryPageRequestDto.builder().categoryName("Продукты").month(month).year(year).build();
    }

    // ─── категория не найдена ──────────────────────────────────────────────

    @Test
    @DisplayName("Должен выбросить EntityNotFoundException для несуществующей категории")
    void shouldThrowEntityNotFoundForUnknownCategory() {
        when(categoryRepository.findByNameAndUserId("Нет такой", userId)).thenReturn(Optional.empty());
        CategoryPageRequestDto request = CategoryPageRequestDto.builder()
                .categoryName("Нет такой").month(9).year(2026).build();

        assertThatThrownBy(() -> categoryPageService.getPage(userId, request, LocalDate.of(2026, 9, 11)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Должен выбросить EntityNotFoundException для категории другого пользователя")
    void shouldThrowEntityNotFoundForCategoryOfAnotherUser() {
        UUID otherUserId = UUID.randomUUID();
        when(categoryRepository.findByNameAndUserId("Продукты", otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryPageService.getPage(otherUserId, request(9, 2026), LocalDate.of(2026, 9, 11)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ─── системная категория ───────────────────────────────────────────────

    @Test
    @DisplayName("Должен вернуть system=true для системной категории")
    void shouldReturnSystemTrueForSystemCategory() {
        Category systemCategory = Category.builder()
                .id(categoryId).userId(userId).name("Инвестиции").system(true).budget(BigDecimal.ZERO).build();
        when(categoryRepository.findByNameAndUserId("Инвестиции", userId)).thenReturn(Optional.of(systemCategory));

        CategoryPageResponseDto result = categoryPageService.getPage(userId,
                CategoryPageRequestDto.builder().categoryName("Инвестиции").month(9).year(2026).build(),
                LocalDate.of(2026, 9, 11));

        assertThat(result.getCategory().isSystem()).isTrue();
    }

    // ─── dayOfMonth ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Должен вернуть dayOfMonth равным сегодняшнему числу для текущего месяца")
    void shouldReturnDayOfMonthAsTodayForCurrentMonth() {
        LocalDate today = LocalDate.of(2026, 9, 11);

        CategoryPageResponseDto result = categoryPageService.getPage(userId, request(9, 2026), today);

        assertThat(result.getDayOfMonth()).isEqualTo(11);
        assertThat(result.getDaysInMonth()).isEqualTo(30);
    }

    @Test
    @DisplayName("Должен вернуть dayOfMonth равным длине месяца для прошлого месяца")
    void shouldReturnDayOfMonthAsMonthLengthForPastMonth() {
        LocalDate today = LocalDate.of(2026, 9, 11);

        CategoryPageResponseDto result = categoryPageService.getPage(userId, request(8, 2026), today);

        assertThat(result.getDayOfMonth()).isEqualTo(31);
    }

    @Test
    @DisplayName("Должен вернуть dayOfMonth равным 0 для будущего месяца")
    void shouldReturnZeroDayOfMonthForFutureMonth() {
        LocalDate today = LocalDate.of(2026, 9, 11);

        CategoryPageResponseDto result = categoryPageService.getPage(userId, request(10, 2026), today);

        assertThat(result.getDayOfMonth()).isZero();
    }

    // ─── months (12 элементов, единственный partial) ───────────────────────

    @Test
    @DisplayName("months: должен вернуть 12 элементов M-11..M с нулями и единственным partial для текущего месяца")
    void shouldReturnTwelveChartMonthsWithZerosAndSinglePartial() {
        LocalDate today = LocalDate.of(2026, 9, 11);
        when(expenseRepository.findMonthlyNonTransferExpenseByCategoryAndDateBetween(eq(userId), eq(categoryId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{2026, 9, new BigDecimal("500.00")}));

        CategoryPageResponseDto result = categoryPageService.getPage(userId, request(9, 2026), today);

        assertThat(result.getMonths()).hasSize(12);
        assertThat(result.getMonths().getFirst().getMonth()).isEqualTo(10);
        assertThat(result.getMonths().getFirst().getYear()).isEqualTo(2025);
        assertThat(result.getMonths().getLast().getMonth()).isEqualTo(9);
        assertThat(result.getMonths().getLast().getYear()).isEqualTo(2026);
        assertThat(result.getMonths().getLast().getAmount()).isEqualByComparingTo("500.00");
        assertThat(result.getMonths().getLast().isPartial()).isTrue();

        long partialCount = result.getMonths().stream().filter(CategoryMonthAmountDto::isPartial).count();
        assertThat(partialCount).isEqualTo(1);

        // Месяцы без записей — нулевые
        assertThat(result.getMonths().getFirst().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("months: текущий месяц не partial, если сегодня — последний день месяца")
    void shouldNotMarkCurrentMonthPartialOnLastDayOfMonth() {
        LocalDate lastDayOfMonth = LocalDate.of(2026, 9, 30);

        CategoryPageResponseDto result = categoryPageService.getPage(userId, request(9, 2026), lastDayOfMonth);

        assertThat(result.getMonths().getLast().isPartial()).isFalse();
    }

    // ─── spent ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("spent: должен равняться сумме операций категории за месяц")
    void spentShouldEqualSumOfOperations() {
        LocalDate today = LocalDate.of(2026, 9, 11);
        when(expenseRepository.findMonthlyNonTransferExpenseByCategoryAndDateBetween(eq(userId), eq(categoryId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{2026, 9, new BigDecimal("3000.00")}));
        Expense e1 = Expense.builder().id(UUID.randomUUID()).userId(userId).category(category)
                .amount(new BigDecimal("1000.00")).date(today).build();
        Expense e2 = Expense.builder().id(UUID.randomUUID()).userId(userId).category(category)
                .amount(new BigDecimal("2000.00")).date(today.minusDays(1)).build();
        when(expenseRepository.findByUserIdAndCategoryIdAndDateBetweenOrderByDateDesc(eq(userId), eq(categoryId), any(), any()))
                .thenReturn(List.of(e1, e2));
        when(expenseMapper.toOperationDto(any())).thenReturn(OperationDto.builder().build());

        CategoryPageResponseDto result = categoryPageService.getPage(userId, request(9, 2026), today);

        assertThat(result.getSpent()).isEqualByComparingTo("3000.00");
        assertThat(result.getOperationsCount()).isEqualTo(2);
        assertThat(result.getAverageCheck()).isEqualByComparingTo("1500.00");
        assertThat(result.getLargestAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("operationsCount/averageCheck/largestAmount: должны быть null/0 при отсутствии операций")
    void shouldReturnNullStatsWhenNoOperations() {
        CategoryPageResponseDto result = categoryPageService.getPage(userId, request(9, 2026), LocalDate.of(2026, 9, 11));

        assertThat(result.getOperationsCount()).isZero();
        assertThat(result.getAverageCheck()).isNull();
        assertThat(result.getLargestAmount()).isNull();
    }

    // ─── sharePercent ───────────────────────────────────────────────────────

    @Test
    @DisplayName("sharePercent: должен рассчитать долю категории в расходах за окно графика")
    void shouldCalculateSharePercent() {
        LocalDate today = LocalDate.of(2026, 9, 11);
        when(expenseRepository.findMonthlyNonTransferExpenseByCategoryAndDateBetween(eq(userId), eq(categoryId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{2026, 9, new BigDecimal("2700.00")}));
        when(expenseRepository.findMonthlyNonTransferExpenseByUserIdAndDateBetween(eq(userId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{2026, 9, new BigDecimal("10000.00")}));

        CategoryPageResponseDto result = categoryPageService.getPage(userId, request(9, 2026), today);

        assertThat(result.getSharePercent()).isEqualByComparingTo("27.0");
    }

    @Test
    @DisplayName("sharePercent: должен быть null, если всех расходов за окно нет")
    void shouldReturnNullSharePercentWhenNoExpensesInWindow() {
        CategoryPageResponseDto result = categoryPageService.getPage(userId, request(9, 2026), LocalDate.of(2026, 9, 11));

        assertThat(result.getSharePercent()).isNull();
    }

    // ─── norm / normMonthsCounted ───────────────────────────────────────────

    @Test
    @DisplayName("norm: должен рассчитываться через строки той же категории, что вернул NormWindowLoader")
    void shouldCalculateNormUsingCategoryDataMonthsFromLoader() {
        LocalDate today = LocalDate.of(2026, 9, 11);
        List<MonthlyAggregateRow> categoryRows = List.of(
                new MonthlyAggregateRow(2026, 8, new BigDecimal("900.00"), new BigDecimal("2700.00"), true));
        NormsContext context = new NormsContext(List.of(), List.of(), List.of(), Map.of(), Map.of());
        when(normWindowLoader.loadForMonth(eq(userId), eq(LocalDate.of(2026, 9, 1)), eq(11))).thenReturn(context);
        when(normWindowLoader.categoryDataMonths(context, categoryId)).thenReturn(categoryRows);

        NormComparisonDto expectedNorm = NormComparisonDto.builder()
                .status(NormStatus.NORMAL).usualByDay(new BigDecimal("900.00"))
                .averageMonthly(new BigDecimal("2700.00")).deviationPercent(BigDecimal.ZERO).build();
        when(normCalculationService.calculateNorm(eq(categoryRows), any(), eq(11))).thenReturn(expectedNorm);

        CategoryPageResponseDto result = categoryPageService.getPage(userId, request(9, 2026), today);

        assertThat(result.getNorm()).isEqualTo(expectedNorm);
        assertThat(result.getNormMonthsCounted()).isEqualTo(1);
        verify(normCalculationService).calculateNorm(eq(categoryRows), any(), eq(11));
    }

    @Test
    @DisplayName("normMonthsCounted: должен быть 0 при статусе NO_HISTORY")
    void shouldReturnZeroNormMonthsCountedForNoHistory() {
        when(normWindowLoader.categoryDataMonths(any(), any())).thenReturn(
                List.of(new MonthlyAggregateRow(2026, 8, BigDecimal.ZERO, BigDecimal.ZERO, false)));
        when(normCalculationService.calculateNorm(any(), any(), anyInt()))
                .thenReturn(NormComparisonDto.builder().status(NormStatus.NO_HISTORY).build());

        CategoryPageResponseDto result = categoryPageService.getPage(userId, request(9, 2026), LocalDate.of(2026, 9, 11));

        assertThat(result.getNormMonthsCounted()).isZero();
    }

    @Test
    @DisplayName("Должен вернуть NO_HISTORY, если окно норм пустое")
    void shouldReturnNoHistoryWhenNormWindowIsEmpty() {
        CategoryPageResponseDto result = categoryPageService.getPage(userId, request(9, 2026), LocalDate.of(2026, 9, 11));

        assertThat(result.getNorm().getStatus()).isEqualTo(NormStatus.NO_HISTORY);
    }
}
