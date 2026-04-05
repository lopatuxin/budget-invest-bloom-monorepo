package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pyc.lopatuxin.budget.dto.response.MetricResponseDto;
import pyc.lopatuxin.budget.dto.response.MonthlyMetricDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AbstractMetricService (Template Method)")
class AbstractMetricServiceTest {

    private TestableMetricService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new TestableMetricService();
        userId = UUID.randomUUID();
    }

    // ========== Тестовый наследник ==========

    /**
     * Конкретная реализация AbstractMetricService для тестирования шаблонного метода.
     * Данные задаются извне через setData().
     */
    private static class TestableMetricService extends AbstractMetricService {

        private List<Object[]> data = Collections.emptyList();

        void setData(List<Object[]> data) {
            this.data = data;
        }

        @Override
        protected List<Object[]> findMonthlyData(UUID userId, int year) {
            return data;
        }

        @Override
        protected String getMetricName() {
            return "тестовая метрика";
        }
    }

    // ========== Happy path ==========

    @Test
    @DisplayName("Должен вернуть корректную метрику при данных за несколько месяцев")
    void shouldReturnCorrectMetricForSeveralMonths() {
        int year = 2026;
        service.setData(List.of(
                new Object[]{3, new BigDecimal("100000.00")},
                new Object[]{6, new BigDecimal("150000.00")},
                new Object[]{9, new BigDecimal("120000.00")}
        ));

        MetricResponseDto result = service.getMetric(userId, year);

        assertThat(result).isNotNull();
        assertThat(result.getYear()).isEqualTo(year);
        assertThat(result.getMonthlyData()).hasSize(12);

        // Месяцы с данными
        assertThat(result.getMonthlyData().get(2).getAmount()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(result.getMonthlyData().get(2).getMonthName()).isEqualTo("Мар");
        assertThat(result.getMonthlyData().get(5).getAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(result.getMonthlyData().get(8).getAmount()).isEqualByComparingTo(new BigDecimal("120000.00"));

        // Месяцы без данных = 0
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getMonthlyData().get(11).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // yearlyAverage = (100000 + 150000 + 120000) / 3 = 123333.33
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("123333.33"));

        // yearlyMax = 150000
        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("150000.00"));

        // currentValue = последний ненулевой (Сентябрь)
        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("120000.00"));

        // previousValue = предпоследний ненулевой (Июнь)
        assertThat(result.getPreviousValue()).isEqualByComparingTo(new BigDecimal("150000.00"));

        // changePercent = (120000 - 150000) / 150000 * 100 = -20.0%
        assertThat(result.getChangePercent()).isEqualTo("-20.0%");
    }

    // ========== Граничный случай: пустой год ==========

    @Test
    @DisplayName("Должен вернуть нулевые показатели при отсутствии данных за год")
    void shouldReturnZeroValuesWhenNoDataExists() {
        int year = 2026;
        service.setData(Collections.emptyList());

        MetricResponseDto result = service.getMetric(userId, year);

        assertThat(result.getYear()).isEqualTo(year);
        assertThat(result.getMonthlyData()).hasSize(12);
        assertThat(result.getCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getPreviousValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getYearlyMax()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getChangePercent()).isEqualTo("+0.0%");

        for (MonthlyMetricDto monthly : result.getMonthlyData()) {
            assertThat(monthly.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ========== Граничный случай: один месяц ==========

    @Test
    @DisplayName("Должен корректно обработать один месяц с данными")
    void shouldHandleSingleMonthWithData() {
        int year = 2026;
        service.setData(List.<Object[]>of(
                new Object[]{5, new BigDecimal("200000.00")}
        ));

        MetricResponseDto result = service.getMetric(userId, year);

        assertThat(result.getMonthlyData().get(4).getAmount()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(result.getMonthlyData().get(4).getMonthName()).isEqualTo("Май");

        // yearlyAverage = 200000 / 1 = 200000
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("200000.00"));

        // currentValue = 200000, previousValue = 0 (нет предпоследнего)
        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(result.getPreviousValue()).isEqualByComparingTo(BigDecimal.ZERO);

        // previousValue=0 => TrendFormatter: +0.0%
        assertThat(result.getChangePercent()).isEqualTo("+0.0%");
    }

    // ========== Все 12 месяцев ==========

    @Test
    @DisplayName("Должен корректно обработать данные за все 12 месяцев")
    void shouldHandleAllTwelveMonthsWithData() {
        int year = 2026;
        service.setData(List.of(
                new Object[]{1, new BigDecimal("100000")},
                new Object[]{2, new BigDecimal("110000")},
                new Object[]{3, new BigDecimal("105000")},
                new Object[]{4, new BigDecimal("120000")},
                new Object[]{5, new BigDecimal("115000")},
                new Object[]{6, new BigDecimal("130000")},
                new Object[]{7, new BigDecimal("125000")},
                new Object[]{8, new BigDecimal("140000")},
                new Object[]{9, new BigDecimal("135000")},
                new Object[]{10, new BigDecimal("150000")},
                new Object[]{11, new BigDecimal("145000")},
                new Object[]{12, new BigDecimal("160000")}
        ));

        MetricResponseDto result = service.getMetric(userId, year);

        assertThat(result.getMonthlyData()).hasSize(12);

        for (MonthlyMetricDto monthly : result.getMonthlyData()) {
            assertThat(monthly.getAmount()).isGreaterThan(BigDecimal.ZERO);
        }

        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("160000"));

        // yearlyAverage = 1535000 / 12 = 127916.67
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("127916.67"));

        // currentValue = декабрь, previousValue = ноябрь
        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("160000"));
        assertThat(result.getPreviousValue()).isEqualByComparingTo(new BigDecimal("145000"));

        // changePercent = (160000 - 145000) / 145000 * 100 = +10.3%
        assertThat(result.getChangePercent()).isEqualTo("+10.3%");
    }

    // ========== Среднее только по ненулевым месяцам ==========

    @Test
    @DisplayName("Должен рассчитать среднее только по ненулевым месяцам")
    void shouldCalculateAverageOnlyForNonZeroMonths() {
        int year = 2026;
        service.setData(List.of(
                new Object[]{1, new BigDecimal("50000")},
                new Object[]{12, new BigDecimal("70000")}
        ));

        MetricResponseDto result = service.getMetric(userId, year);

        // Среднее = (50000 + 70000) / 2 = 60000, а не / 12
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("60000.00"));
    }

    // ========== currentValue и previousValue из непоследовательных месяцев ==========

    @Test
    @DisplayName("Должен определить currentValue и previousValue из непоследовательных месяцев")
    void shouldSelectCurrentAndPreviousFromNonConsecutiveMonths() {
        int year = 2026;
        service.setData(List.of(
                new Object[]{2, new BigDecimal("80000")},
                new Object[]{10, new BigDecimal("95000")}
        ));

        MetricResponseDto result = service.getMetric(userId, year);

        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("95000"));
        assertThat(result.getPreviousValue()).isEqualByComparingTo(new BigDecimal("80000"));

        // changePercent = (95000 - 80000) / 80000 * 100 = +18.8%
        assertThat(result.getChangePercent()).isEqualTo("+18.8%");
    }

    // ========== Названия месяцев ==========

    @Test
    @DisplayName("Должен корректно заполнить названия всех 12 месяцев")
    void shouldPopulateAllMonthNamesCorrectly() {
        service.setData(Collections.emptyList());

        MetricResponseDto result = service.getMetric(userId, 2026);

        String[] expectedNames = {"Янв", "Фев", "Мар", "Апр", "Май", "Июн",
                "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"};

        for (int i = 0; i < 12; i++) {
            MonthlyMetricDto monthly = result.getMonthlyData().get(i);
            assertThat(monthly.getMonth()).isEqualTo(i + 1);
            assertThat(monthly.getMonthName()).isEqualTo(expectedNames[i]);
        }
    }

    // ========== yearlyMax ==========

    @Test
    @DisplayName("Должен корректно определить yearlyMax среди нескольких месяцев")
    void shouldDetermineYearlyMaxCorrectly() {
        int year = 2026;
        service.setData(List.of(
                new Object[]{3, new BigDecimal("50000")},
                new Object[]{7, new BigDecimal("250000")},
                new Object[]{11, new BigDecimal("180000")}
        ));

        MetricResponseDto result = service.getMetric(userId, year);

        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("250000"));
    }

    // ========== Отрицательный тренд ==========

    @Test
    @DisplayName("Должен вернуть отрицательный changePercent при снижении значения")
    void shouldReturnNegativeChangePercentWhenValueDecreased() {
        int year = 2026;
        service.setData(List.of(
                new Object[]{3, new BigDecimal("200000")},
                new Object[]{7, new BigDecimal("160000")}
        ));

        MetricResponseDto result = service.getMetric(userId, year);

        // (160000 - 200000) / 200000 * 100 = -20.0%
        assertThat(result.getChangePercent()).isEqualTo("-20.0%");
    }

    // ========== Положительный тренд ==========

    @Test
    @DisplayName("Должен вернуть положительный changePercent при росте значения")
    void shouldReturnPositiveChangePercentWhenValueIncreased() {
        int year = 2026;
        service.setData(List.of(
                new Object[]{1, new BigDecimal("100000")},
                new Object[]{2, new BigDecimal("150000")}
        ));

        MetricResponseDto result = service.getMetric(userId, year);

        // (150000 - 100000) / 100000 * 100 = +50.0%
        assertThat(result.getChangePercent()).isEqualTo("+50.0%");
    }

    // ========== getMetricName() вызывается корректно ==========

    @Test
    @DisplayName("Тестовый наследник должен возвращать правильное имя метрики")
    void shouldReturnCorrectMetricName() {
        assertThat(service.getMetricName()).isEqualTo("тестовая метрика");
    }
}
