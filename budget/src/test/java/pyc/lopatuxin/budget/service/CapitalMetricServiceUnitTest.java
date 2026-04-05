package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.response.MetricResponseDto;
import pyc.lopatuxin.budget.dto.response.MonthlyMetricDto;
import pyc.lopatuxin.budget.repository.CapitalRecordRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CapitalMetricService")
class CapitalMetricServiceUnitTest {

    @Mock
    private CapitalRecordRepository capitalRecordRepository;

    @InjectMocks
    private CapitalMetricService capitalMetricService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Должен вернуть корректную метрику при наличии данных за несколько месяцев")
    void shouldReturnCorrectMetricWhenSeveralMonthsHaveData() {
        int year = 2026;
        List<Object[]> dbData = List.of(
                new Object[]{3, new BigDecimal("100000.00")},
                new Object[]{6, new BigDecimal("150000.00")},
                new Object[]{9, new BigDecimal("120000.00")}
        );
        when(capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year)).thenReturn(dbData);

        MetricResponseDto result = capitalMetricService.getCapitalMetric(userId, year);

        assertThat(result).isNotNull();
        assertThat(result.getYear()).isEqualTo(year);
        assertThat(result.getMonthlyData()).hasSize(12);

        // Проверяем что месяцы с данными заполнены корректно
        assertThat(result.getMonthlyData().get(2).getAmount()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(result.getMonthlyData().get(2).getMonthName()).isEqualTo("Мар");
        assertThat(result.getMonthlyData().get(5).getAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(result.getMonthlyData().get(8).getAmount()).isEqualByComparingTo(new BigDecimal("120000.00"));

        // Проверяем что месяцы без данных имеют amount = 0
        assertThat(result.getMonthlyData().get(0).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getMonthlyData().get(11).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // yearlyAverage = (100000 + 150000 + 120000) / 3 = 123333.33
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("123333.33"));

        // yearlyMax = 150000
        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("150000.00"));

        // currentValue = последний ненулевой = Сентябрь = 120000
        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("120000.00"));

        // previousValue = предпоследний ненулевой = Июнь = 150000
        assertThat(result.getPreviousValue()).isEqualByComparingTo(new BigDecimal("150000.00"));

        // changePercent = (120000 - 150000) / 150000 * 100 = -20.0%
        assertThat(result.getChangePercent()).isEqualTo("-20.0%");

        verify(capitalRecordRepository).findMonthlyCapitalByUserIdAndYear(userId, year);
    }

    @Test
    @DisplayName("Должен вернуть нулевые показатели при отсутствии данных за год")
    void shouldReturnZeroValuesWhenNoDataExists() {
        int year = 2026;
        when(capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year)).thenReturn(Collections.emptyList());

        MetricResponseDto result = capitalMetricService.getCapitalMetric(userId, year);

        assertThat(result.getYear()).isEqualTo(year);
        assertThat(result.getMonthlyData()).hasSize(12);
        assertThat(result.getCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getPreviousValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getYearlyMax()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getChangePercent()).isEqualTo("+0.0%");

        // Все 12 месяцев должны иметь amount = 0
        for (MonthlyMetricDto monthly : result.getMonthlyData()) {
            assertThat(monthly.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("Должен корректно обработать один месяц с данными")
    void shouldHandleSingleMonthWithData() {
        int year = 2026;
        List<Object[]> dbData = List.<Object[]>of(
                new Object[]{5, new BigDecimal("200000.00")}
        );
        when(capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year)).thenReturn(dbData);

        MetricResponseDto result = capitalMetricService.getCapitalMetric(userId, year);

        assertThat(result.getMonthlyData().get(4).getAmount()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(result.getMonthlyData().get(4).getMonthName()).isEqualTo("Май");

        // yearlyAverage = 200000 / 1 = 200000
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("200000.00"));

        // currentValue = 200000, previousValue = 0 (нет предпоследнего)
        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(result.getPreviousValue()).isEqualByComparingTo(BigDecimal.ZERO);

        // previousValue=0 => TrendFormatter возвращает +0.0%
        assertThat(result.getChangePercent()).isEqualTo("+0.0%");
    }

    @Test
    @DisplayName("Должен корректно обработать данные за все 12 месяцев")
    void shouldHandleAllTwelveMonthsWithData() {
        int year = 2026;
        List<Object[]> dbData = List.of(
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
        );
        when(capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year)).thenReturn(dbData);

        MetricResponseDto result = capitalMetricService.getCapitalMetric(userId, year);

        assertThat(result.getMonthlyData()).hasSize(12);

        // Ни один месяц не должен быть нулевым
        for (MonthlyMetricDto monthly : result.getMonthlyData()) {
            assertThat(monthly.getAmount()).isGreaterThan(BigDecimal.ZERO);
        }

        // yearlyMax = 160000
        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("160000"));

        // yearlyAverage = (100000+110000+105000+120000+115000+130000+125000+140000+135000+150000+145000+160000) / 12
        // = 1535000 / 12 = 127916.67
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("127916.67"));

        // currentValue = декабрь = 160000, previousValue = ноябрь = 145000
        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("160000"));
        assertThat(result.getPreviousValue()).isEqualByComparingTo(new BigDecimal("145000"));

        // changePercent = (160000 - 145000) / 145000 * 100 = +10.3%
        assertThat(result.getChangePercent()).isEqualTo("+10.3%");
    }

    @Test
    @DisplayName("Должен корректно рассчитать среднее только по ненулевым месяцам")
    void shouldCalculateYearlyAverageOnlyForNonZeroMonths() {
        int year = 2026;
        // 2 месяца: 50000 + 70000 = 120000; average = 60000
        List<Object[]> dbData = List.of(
                new Object[]{1, new BigDecimal("50000")},
                new Object[]{12, new BigDecimal("70000")}
        );
        when(capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year)).thenReturn(dbData);

        MetricResponseDto result = capitalMetricService.getCapitalMetric(userId, year);

        // Среднее считается по 2 ненулевым месяцам, а не по 12
        assertThat(result.getYearlyAverage()).isEqualByComparingTo(new BigDecimal("60000.00"));
    }

    @Test
    @DisplayName("Должен корректно определить currentValue и previousValue из непоследовательных месяцев")
    void shouldSelectCurrentAndPreviousFromNonConsecutiveMonths() {
        int year = 2026;
        // Данные за Февраль и Октябрь — currentValue=Октябрь, previousValue=Февраль
        List<Object[]> dbData = List.of(
                new Object[]{2, new BigDecimal("80000")},
                new Object[]{10, new BigDecimal("95000")}
        );
        when(capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year)).thenReturn(dbData);

        MetricResponseDto result = capitalMetricService.getCapitalMetric(userId, year);

        assertThat(result.getCurrentValue()).isEqualByComparingTo(new BigDecimal("95000"));
        assertThat(result.getPreviousValue()).isEqualByComparingTo(new BigDecimal("80000"));

        // changePercent = (95000 - 80000) / 80000 * 100 = +18.8%
        assertThat(result.getChangePercent()).isEqualTo("+18.8%");
    }

    @Test
    @DisplayName("Должен корректно заполнить названия всех 12 месяцев")
    void shouldPopulateAllMonthNamesCorrectly() {
        int year = 2026;
        when(capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year)).thenReturn(Collections.emptyList());

        MetricResponseDto result = capitalMetricService.getCapitalMetric(userId, year);

        String[] expectedNames = {"Янв", "Фев", "Мар", "Апр", "Май", "Июн",
                "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"};

        for (int i = 0; i < 12; i++) {
            MonthlyMetricDto monthly = result.getMonthlyData().get(i);
            assertThat(monthly.getMonth()).isEqualTo(i + 1);
            assertThat(monthly.getMonthName()).isEqualTo(expectedNames[i]);
        }
    }

    @Test
    @DisplayName("Должен корректно определить yearlyMax среди нескольких месяцев")
    void shouldDetermineYearlyMaxCorrectly() {
        int year = 2026;
        List<Object[]> dbData = List.of(
                new Object[]{3, new BigDecimal("50000")},
                new Object[]{7, new BigDecimal("250000")},
                new Object[]{11, new BigDecimal("180000")}
        );
        when(capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year)).thenReturn(dbData);

        MetricResponseDto result = capitalMetricService.getCapitalMetric(userId, year);

        assertThat(result.getYearlyMax()).isEqualByComparingTo(new BigDecimal("250000"));
    }

    @Test
    @DisplayName("Должен вернуть отрицательный changePercent при снижении капитала")
    void shouldReturnNegativeChangePercentWhenCapitalDecreased() {
        int year = 2026;
        // previousValue = Март = 200000, currentValue = Июль = 160000
        List<Object[]> dbData = List.of(
                new Object[]{3, new BigDecimal("200000")},
                new Object[]{7, new BigDecimal("160000")}
        );
        when(capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year)).thenReturn(dbData);

        MetricResponseDto result = capitalMetricService.getCapitalMetric(userId, year);

        // (160000 - 200000) / 200000 * 100 = -20.0%
        assertThat(result.getChangePercent()).isEqualTo("-20.0%");
    }

    @Test
    @DisplayName("Должен корректно вызывать репозиторий с правильными параметрами")
    void shouldCallRepositoryWithCorrectParams() {
        int year = 2025;
        when(capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year)).thenReturn(Collections.emptyList());

        capitalMetricService.getCapitalMetric(userId, year);

        verify(capitalRecordRepository).findMonthlyCapitalByUserIdAndYear(userId, year);
    }

    @Test
    @DisplayName("getMetricName должен возвращать 'капитала'")
    void shouldReturnCorrectMetricName() {
        assertThat(capitalMetricService.getMetricName()).isEqualTo("капитала");
    }

    @Test
    @DisplayName("Должен вернуть положительный changePercent при росте капитала")
    void shouldReturnPositiveChangePercentWhenCapitalIncreased() {
        int year = 2026;
        List<Object[]> dbData = List.of(
                new Object[]{3, new BigDecimal("80000")},
                new Object[]{7, new BigDecimal("150000")}
        );
        when(capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year)).thenReturn(dbData);

        MetricResponseDto result = capitalMetricService.getCapitalMetric(userId, year);

        // changePercent = (150000 - 80000) / 80000 * 100 = +87.5%
        assertThat(result.getChangePercent()).isEqualTo("+87.5%");
    }
}
