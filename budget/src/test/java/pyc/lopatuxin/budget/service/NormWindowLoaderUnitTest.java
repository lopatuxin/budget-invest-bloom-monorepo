package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;
import pyc.lopatuxin.budget.service.NormCalculationService.MonthlyAggregateRow;
import pyc.lopatuxin.budget.service.NormWindowLoader.NormsContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NormWindowLoaderUnitTest")
class NormWindowLoaderUnitTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @InjectMocks
    private NormWindowLoader normWindowLoader;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    // ─── границы окна ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Должен запросить окно ровно за 12 месяцев перед запрошенным месяцем (M-12..M-1)")
    void shouldQueryExactlyTwelveMonthsBeforeRequestedMonth() {
        LocalDate requestedMonthStart = LocalDate.of(2026, 9, 1);
        when(expenseRepository.findWindowedNonTransferExpenseStats(any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        when(incomeRepository.findWindowedNonTransferIncomeStats(any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        when(expenseRepository.findWindowedNonTransferExpenseStatsByCategory(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        normWindowLoader.loadForMonth(userId, requestedMonthStart, 11);

        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(expenseRepository).findWindowedNonTransferExpenseStats(
                eq(userId), startCaptor.capture(), endCaptor.capture(), eq(11));

        assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 8, 31));

        verify(incomeRepository).findWindowedNonTransferIncomeStats(
                eq(userId), eq(LocalDate.of(2025, 9, 1)), eq(LocalDate.of(2026, 8, 31)), eq(11));
        verify(expenseRepository).findWindowedNonTransferExpenseStatsByCategory(
                eq(userId), eq(LocalDate.of(2025, 9, 1)), eq(LocalDate.of(2026, 8, 31)), eq(11));
    }

    @Test
    @DisplayName("Должен собрать dataMonths и признак подневности из строк общих расходов")
    void shouldBuildDataMonthsAndDailyGranularityFromOverallExpenseRows() {
        LocalDate requestedMonthStart = LocalDate.of(2026, 9, 1);
        // Март: 1 отдельный день (месячная сумма без подневного учёта)
        // Апрель: 3 разных дня (подневный учёт)
        when(expenseRepository.findWindowedNonTransferExpenseStats(any(), any(), any(), anyInt()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{2026, 3, new BigDecimal("1000.00"), new BigDecimal("5000.00"), 1L},
                        new Object[]{2026, 4, new BigDecimal("2000.00"), new BigDecimal("6000.00"), 3L}
                ));
        when(incomeRepository.findWindowedNonTransferIncomeStats(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(expenseRepository.findWindowedNonTransferExpenseStatsByCategory(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        NormsContext context = normWindowLoader.loadForMonth(userId, requestedMonthStart, 15);

        assertThat(context.dataMonths()).containsExactlyInAnyOrder(YearMonth.of(2026, 3), YearMonth.of(2026, 4));
        assertThat(context.expenseDailyGranularityByMonth().get(YearMonth.of(2026, 3))).isFalse();
        assertThat(context.expenseDailyGranularityByMonth().get(YearMonth.of(2026, 4))).isTrue();

        MonthlyAggregateRow marchRow = context.expenseDataMonths().stream()
                .filter(row -> row.month() == 3).findFirst().orElseThrow();
        assertThat(marchRow.cutoffAmount()).isEqualByComparingTo("1000.00");
        assertThat(marchRow.fullAmount()).isEqualByComparingTo("5000.00");
    }

    // ─── categoryDataMonths ────────────────────────────────────────────────

    @Test
    @DisplayName("categoryDataMonths: должен вернуть нулевую строку для месяца без записей категории")
    void categoryDataMonths_shouldZeroFillMonthWithoutCategoryRecords() {
        LocalDate requestedMonthStart = LocalDate.of(2026, 9, 1);
        UUID categoryId = UUID.randomUUID();
        UUID otherCategoryId = UUID.randomUUID();

        when(expenseRepository.findWindowedNonTransferExpenseStats(any(), any(), any(), anyInt()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{2026, 3, new BigDecimal("1000.00"), new BigDecimal("5000.00"), 3L}
                ));
        when(incomeRepository.findWindowedNonTransferIncomeStats(any(), any(), any(), anyInt())).thenReturn(List.of());
        // Только другая категория имеет записи за март — искомая категория должна получить нулевую строку
        when(expenseRepository.findWindowedNonTransferExpenseStatsByCategory(any(), any(), any(), anyInt()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{2026, 3, otherCategoryId, new BigDecimal("400.00"), new BigDecimal("1000.00")}
                ));

        NormsContext context = normWindowLoader.loadForMonth(userId, requestedMonthStart, 15);
        List<MonthlyAggregateRow> rows = normWindowLoader.categoryDataMonths(context, categoryId);

        assertThat(rows).hasSize(1);
        MonthlyAggregateRow row = rows.getFirst();
        assertThat(row.year()).isEqualTo(2026);
        assertThat(row.month()).isEqualTo(3);
        assertThat(row.cutoffAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.fullAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        // Нулевая строка должна нести общий признак подневности расходов за месяц, а не свой
        assertThat(row.dailyGranularity()).isTrue();
    }

    @Test
    @DisplayName("categoryDataMonths: должен вернуть реальную строку категории, когда записи есть")
    void categoryDataMonths_shouldReturnActualCategoryRowWhenPresent() {
        LocalDate requestedMonthStart = LocalDate.of(2026, 9, 1);
        UUID categoryId = UUID.randomUUID();

        when(expenseRepository.findWindowedNonTransferExpenseStats(any(), any(), any(), anyInt()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{2026, 5, new BigDecimal("2000.00"), new BigDecimal("9000.00"), 2L}
                ));
        when(incomeRepository.findWindowedNonTransferIncomeStats(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(expenseRepository.findWindowedNonTransferExpenseStatsByCategory(any(), any(), any(), anyInt()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{2026, 5, categoryId, new BigDecimal("800.00"), new BigDecimal("3000.00")}
                ));

        NormsContext context = normWindowLoader.loadForMonth(userId, requestedMonthStart, 15);
        List<MonthlyAggregateRow> rows = normWindowLoader.categoryDataMonths(context, categoryId);

        assertThat(rows).hasSize(1);
        MonthlyAggregateRow row = rows.getFirst();
        assertThat(row.cutoffAmount()).isEqualByComparingTo("800.00");
        assertThat(row.fullAmount()).isEqualByComparingTo("3000.00");
        assertThat(row.dailyGranularity()).isTrue();
    }

    @Test
    @DisplayName("categoryDataMonths: должен вернуть пустой список, если окно не имеет данных ни по одному месяцу")
    void categoryDataMonths_shouldReturnEmptyListWhenWindowHasNoData() {
        LocalDate requestedMonthStart = LocalDate.of(2026, 9, 1);
        UUID categoryId = UUID.randomUUID();

        when(expenseRepository.findWindowedNonTransferExpenseStats(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(incomeRepository.findWindowedNonTransferIncomeStats(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(expenseRepository.findWindowedNonTransferExpenseStatsByCategory(any(), any(), any(), anyInt())).thenReturn(List.of());

        NormsContext context = normWindowLoader.loadForMonth(userId, requestedMonthStart, 15);
        List<MonthlyAggregateRow> rows = normWindowLoader.categoryDataMonths(context, categoryId);

        assertThat(rows).isEmpty();
    }
}
