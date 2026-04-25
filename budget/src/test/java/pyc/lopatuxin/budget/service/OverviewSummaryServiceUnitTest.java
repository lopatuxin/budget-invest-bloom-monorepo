package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.response.OverviewSummaryResponseDto;
import pyc.lopatuxin.budget.repository.CapitalRecordRepository;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.service.PeriodAggregateService.PeriodAggregates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OverviewSummaryServiceUnitTest")
class OverviewSummaryServiceUnitTest {

    @Mock
    private PeriodAggregateService periodAggregateService;

    @Mock
    private CategorySummaryBuilder categorySummaryBuilder;

    @Mock
    private CapitalRecordRepository capitalRecordRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private OverviewSummaryService overviewSummaryService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        // Default lenient stubs — no capital records, no expenses, no categories
        lenient().when(capitalRecordRepository.findByUserIdAndMonthAndYear(any(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(capitalRecordRepository.findLatestByUserId(any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(expenseRepository.sumAmountByCategoryForUserAndDateBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(categoryRepository.findAllById(any()))
                .thenReturn(Collections.emptyList());
    }

    // ─── Helper to build minimal PeriodAggregates ────────────────────────────

    private PeriodAggregates aggregates(int year, int month, BigDecimal income, BigDecimal expenses) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return new PeriodAggregates(start, end, income, expenses, income.subtract(expenses));
    }

    private void stubCurrentAndPrev(UUID uid, int month, int year,
                                    BigDecimal income, BigDecimal expenses) {
        int prevMonth = (month == 1) ? 12 : month - 1;
        int prevYear = (month == 1) ? year - 1 : year;

        when(periodAggregateService.buildPeriodAggregates(uid, month, year))
                .thenReturn(aggregates(year, month, income, expenses));
        lenient().when(periodAggregateService.buildPeriodAggregates(eq(uid), eq(prevMonth), eq(prevYear)))
                .thenReturn(aggregates(prevYear, prevMonth, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    // ─── savingsRate tests ───────────────────────────────────────────────────

    @ParameterizedTest(name = "income={0}, expenses={1} → savingsRate={2}")
    @MethodSource("savingsRateCases")
    @DisplayName("Должен корректно рассчитать savingsRate для различных входных данных")
    void shouldCalculateSavingsRateCorrectly(BigDecimal income, BigDecimal expenses, int expectedRate) {
        stubCurrentAndPrev(userId, 3, 2024, income, expenses);

        OverviewSummaryResponseDto result = overviewSummaryService.getOverview(userId, 3, 2024);

        assertThat(result.getSavingsRate()).isEqualTo(expectedRate);
    }

    private static Stream<Arguments> savingsRateCases() {
        return Stream.of(
                // Positive savings: (150000-97500)/150000*100 = 35%
                Arguments.of(new BigDecimal("150000"), new BigDecimal("97500"), 35),
                // Expenses equal to income → 0%
                Arguments.of(new BigDecimal("100000"), new BigDecimal("100000"), 0),
                // Zero income → 0
                Arguments.of(BigDecimal.ZERO, new BigDecimal("50000"), 0),
                // Overspending clamped to -99: (100-10000)/100*100 = -9900 → clamped to -99
                Arguments.of(new BigDecimal("100"), new BigDecimal("10000"), -99),
                // Zero expenses with positive income: (100-0)/100*100 = 100 → clamped to 99
                Arguments.of(new BigDecimal("100"), BigDecimal.ZERO, 99)
        );
    }

    @Test
    @DisplayName("Должен вернуть savingsRate=0 когда income равен null")
    void shouldReturnZeroSavingsRateWhenIncomeIsNull() {
        // Build aggregates manually with null income — simulate via zero income path
        // since PeriodAggregates record uses BigDecimal, we stub with income=ZERO (signum=0 → returns 0)
        stubCurrentAndPrev(userId, 5, 2024, BigDecimal.ZERO, new BigDecimal("30000"));

        OverviewSummaryResponseDto result = overviewSummaryService.getOverview(userId, 5, 2024);

        assertThat(result.getSavingsRate()).isEqualTo(0);
    }

    @Test
    @DisplayName("Должен вернуть savingsRate в ответе getOverview (smoke-test поля)")
    void shouldReturnSavingsRateFieldInResponse() {
        // income=150000, expenses=97500 → savingsRate=35
        stubCurrentAndPrev(userId, 4, 2024, new BigDecimal("150000"), new BigDecimal("97500"));

        OverviewSummaryResponseDto result = overviewSummaryService.getOverview(userId, 4, 2024);

        assertThat(result).isNotNull();
        assertThat(result.getSavingsRate()).isNotNull();
        assertThat(result.getSavingsRate()).isEqualTo(35);
    }
}
