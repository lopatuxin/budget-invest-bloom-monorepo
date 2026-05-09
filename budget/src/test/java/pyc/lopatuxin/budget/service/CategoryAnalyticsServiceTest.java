package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.request.CategoryAnalyticsRequestDto;
import pyc.lopatuxin.budget.dto.response.CategoryAnalyticsResponseDto;
import pyc.lopatuxin.budget.dto.response.MonthlyMetricDto;
import pyc.lopatuxin.budget.dto.response.YearlyMetricDto;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.mapper.ExpenseMapper;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CategoryAnalyticsService.
 * Verifies that transfer entries (BUY/SELL with isTransfer=true) are excluded from aggregations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryAnalyticsServiceTest")
class CategoryAnalyticsServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseMapper expenseMapper;

    @InjectMocks
    private CategoryAnalyticsService categoryAnalyticsService;

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
                .budget(new BigDecimal("30000.00"))
                .build();
    }

    // ─── BUY не попадает в месячную агрегацию ────────────────────────────────

    @Test
    @DisplayName("buildMonthlyData: BUY с isTransfer=true не должен фигурировать в помесячной агрегации")
    void getAnalytics_transferBuyShouldNotAppearInMonthlyData() {
        // Репозиторий вызван с findMonthlyNonTransferExpenseByCategoryAndUserIdAndYear —
        // возвращает только обычный расход 1000, BUY 50000 исключён на уровне JPQL.
        int year = 2025;
        when(categoryRepository.findByNameAndUserId("Продукты", userId))
                .thenReturn(Optional.of(category));
        when(expenseRepository.findMonthlyNonTransferExpenseByCategoryAndUserIdAndYear(
                eq(userId), eq(categoryId), eq(year)))
                .thenReturn(List.<Object[]>of(new Object[]{3, new BigDecimal("1000.00")}));  // только март, 1000
        when(expenseRepository.findYearlyNonTransferExpenseByCategoryAndUserId(eq(userId), eq(categoryId)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.findByUserIdAndCategoryIdAndDateBetweenOrderByDateDesc(
                eq(userId), eq(categoryId), any(), any()))
                .thenReturn(Collections.emptyList());
        when(expenseMapper.toDtoList(any())).thenReturn(Collections.emptyList());

        CategoryAnalyticsRequestDto request = CategoryAnalyticsRequestDto.builder()
                .categoryName("Продукты")
                .year(year)
                .month(3)
                .build();

        CategoryAnalyticsResponseDto result = categoryAnalyticsService.getAnalytics(userId, request);

        // Март должен содержать 1000, а не 51000 (с учётом transfer BUY 50000)
        MonthlyMetricDto march = result.getMonthlyData().stream()
                .filter(m -> m.getMonth() == 3)
                .findFirst()
                .orElseThrow();
        assertThat(march.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));

        // totalYear = только 1000 (без BUY 50000)
        assertThat(result.getTotalYear()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // ─── BUY не попадает в годовую агрегацию ─────────────────────────────────

    @Test
    @DisplayName("buildYearlyData: BUY с isTransfer=true не должен фигурировать в годовой агрегации")
    void getAnalytics_transferBuyShouldNotAppearInYearlyData() {
        // Репозиторий findYearlyNonTransferExpenseByCategoryAndUserId возвращает только
        // обычный расход 1000 за 2025, BUY 50000 исключён.
        int year = 2025;
        when(categoryRepository.findByNameAndUserId("Продукты", userId))
                .thenReturn(Optional.of(category));
        when(expenseRepository.findMonthlyNonTransferExpenseByCategoryAndUserIdAndYear(
                eq(userId), eq(categoryId), eq(year)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.findYearlyNonTransferExpenseByCategoryAndUserId(eq(userId), eq(categoryId)))
                .thenReturn(List.<Object[]>of(new Object[]{2025, new BigDecimal("1000.00")}));  // только 1000
        when(expenseRepository.findByUserIdAndCategoryIdAndDateBetweenOrderByDateDesc(
                eq(userId), eq(categoryId), any(), any()))
                .thenReturn(Collections.emptyList());
        when(expenseMapper.toDtoList(any())).thenReturn(Collections.emptyList());

        CategoryAnalyticsRequestDto request = CategoryAnalyticsRequestDto.builder()
                .categoryName("Продукты")
                .year(year)
                .month(null)
                .build();

        CategoryAnalyticsResponseDto result = categoryAnalyticsService.getAnalytics(userId, request);

        assertThat(result.getYearlyData()).hasSize(1);
        YearlyMetricDto yearlyDto = result.getYearlyData().getFirst();
        assertThat(yearlyDto.getYear()).isEqualTo(2025);
        // Должно быть 1000, а не 51000 (с учётом transfer BUY 50000)
        assertThat(yearlyDto.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // ─── Оба агрегата одновременно ───────────────────────────────────────────

    @Test
    @DisplayName("getAnalytics: и месячная, и годовая агрегации возвращают 1000, игнорируя BUY 50000")
    void getAnalytics_bothAggregates_shouldReturn1000IgnoringBuy50000() {
        // Синтетика: категория «Продукты», обычный расход 1000 в январе 2025,
        // BUY 50000 в той же категории с isTransfer=true.
        // JPQL-методы с NonTransfer уже отфильтровали BUY — мок возвращает только 1000.
        int year = 2025;
        when(categoryRepository.findByNameAndUserId("Продукты", userId))
                .thenReturn(Optional.of(category));
        when(expenseRepository.findMonthlyNonTransferExpenseByCategoryAndUserIdAndYear(
                eq(userId), eq(categoryId), eq(year)))
                .thenReturn(List.<Object[]>of(new Object[]{1, new BigDecimal("1000.00")}));
        when(expenseRepository.findYearlyNonTransferExpenseByCategoryAndUserId(eq(userId), eq(categoryId)))
                .thenReturn(List.<Object[]>of(new Object[]{2025, new BigDecimal("1000.00")}));
        when(expenseRepository.findByUserIdAndCategoryIdAndDateBetweenOrderByDateDesc(
                eq(userId), eq(categoryId), any(), any()))
                .thenReturn(Collections.emptyList());
        when(expenseMapper.toDtoList(any())).thenReturn(Collections.emptyList());

        CategoryAnalyticsRequestDto request = CategoryAnalyticsRequestDto.builder()
                .categoryName("Продукты")
                .year(year)
                .month(1)
                .build();

        CategoryAnalyticsResponseDto result = categoryAnalyticsService.getAnalytics(userId, request);

        // Месячная: январь = 1000, остальные = 0
        MonthlyMetricDto january = result.getMonthlyData().stream()
                .filter(m -> m.getMonth() == 1)
                .findFirst()
                .orElseThrow();
        assertThat(january.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));

        // Другие месяцы = 0 (BUY не добавился ни к одному)
        long nonZeroMonths = result.getMonthlyData().stream()
                .filter(m -> m.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .count();
        assertThat(nonZeroMonths).isEqualTo(1);

        // Годовая: 2025 = 1000
        assertThat(result.getYearlyData()).hasSize(1);
        assertThat(result.getYearlyData().getFirst().getAmount())
                .isEqualByComparingTo(new BigDecimal("1000.00"));

        // totalYear = 1000 (не 51000)
        assertThat(result.getTotalYear()).isEqualByComparingTo(new BigDecimal("1000.00"));
        // averageYear = 1000 (1 месяц с данными)
        assertThat(result.getAverageYear()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }
}
