package pyc.lopatuxin.budget.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import pyc.lopatuxin.budget.AbstractIntegrationTest;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Интеграционные тесты InflationMetricController")
class InflationMetricControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/budget/metric/inflation";

    private UUID userId;
    private Category category;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        incomeRepository.deleteAll();
        capitalRecordRepository.deleteAll();
        categoryRepository.deleteAll();
        userId = UUID.randomUUID();

        category = categoryRepository.save(Category.builder()
                .userId(userId)
                .name("Тестовая категория")
                .budget(BigDecimal.ZERO)
                .build());
    }

    @Test
    @DisplayName("Должен вернуть статус 200 и корректную метрику инфляции при наличии данных за оба года")
    void shouldReturnOkWithCorrectInflationWhenDataExists() throws Exception {
        // Предыдущий год (2025): расходы на 1 200 000 => среднемесячные = 100 000
        for (int month = 1; month <= 12; month++) {
            expenseRepository.save(Expense.builder()
                    .userId(userId)
                    .category(category)
                    .amount(new BigDecimal("100000.00"))
                    .date(LocalDate.of(2025, month, 15))
                    .build());
        }

        // Текущий год (2026): Январь = 120 000
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("120000.00"))
                .date(LocalDate.of(2026, 1, 10))
                .build());

        String requestBody = buildRequest(userId, 2026);

        // Январь: кумулятивная = 120000, avg = 120000/1 = 120000
        // инфляция = (120000 - 100000) / 100000 * 100 = 20.0%
        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Метрика инфляции успешно получена")))
                .andExpect(jsonPath("$.body.year", is(2026)))
                .andExpect(jsonPath("$.body.monthlyData", hasSize(12)))
                .andExpect(jsonPath("$.body.monthlyData[0].month", is(1)))
                .andExpect(jsonPath("$.body.monthlyData[0].monthName", is("Янв")))
                .andExpect(jsonPath("$.body.monthlyData[0].amount", comparesEqualTo(20.0)))
                .andExpect(jsonPath("$.body.currentValue", notNullValue()))
                .andExpect(jsonPath("$.body.yearlyAverage", notNullValue()))
                .andExpect(jsonPath("$.body.yearlyMax", notNullValue()))
                .andExpect(jsonPath("$.body.changePercent", notNullValue()));
    }

    @Test
    @DisplayName("Должен вернуть ответ в структуре ResponseApi (id, status, message, timestamp, body)")
    void shouldReturnResponseMatchingResponseApiContract() throws Exception {
        String requestBody = buildRequest(userId, 2026);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.body", notNullValue()));
    }

    @Test
    @DisplayName("Должен вернуть статус 200 и нулевые показатели при отсутствии расходов за предыдущий год")
    void shouldReturnOkWithZeroValuesWhenNoPreviousYearData() throws Exception {
        // Только текущий год, предыдущий пуст
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("50000.00"))
                .date(LocalDate.of(2026, 3, 10))
                .build());

        String requestBody = buildRequest(userId, 2026);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.year", is(2026)))
                .andExpect(jsonPath("$.body.currentValue", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.previousValue", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.yearlyAverage", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.yearlyMax", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.monthlyData", hasSize(12)));
    }

    @Test
    @DisplayName("Должен вернуть статус 200 и нулевые показатели при полном отсутствии данных")
    void shouldReturnOkWithZeroValuesWhenNoDataAtAll() throws Exception {
        String requestBody = buildRequest(userId, 2026);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.year", is(2026)))
                .andExpect(jsonPath("$.body.currentValue", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.previousValue", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.yearlyAverage", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.yearlyMax", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.monthlyData", hasSize(12)));
    }

    @Test
    @DisplayName("Должен вернуть статус 400 при отсутствии параметра year (null)")
    void shouldReturnBadRequestWhenYearIsNull() throws Exception {
        String requestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {}
                }
                """.formatted(userId, UUID.randomUUID());

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @MethodSource("invalidYearValues")
    @DisplayName("Должен вернуть статус 400 при некорректном значении year")
    void shouldReturnBadRequestWhenYearIsOutOfRange(int year) throws Exception {
        String requestBody = buildRequest(userId, year);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    private static Stream<Arguments> invalidYearValues() {
        return Stream.of(
                Arguments.of(1949),
                Arguments.of(2101)
        );
    }

    @Test
    @DisplayName("Должен не учитывать расходы другого пользователя")
    void shouldNotIncludeOtherUserData() throws Exception {
        UUID otherUserId = UUID.randomUUID();

        Category otherCategory = categoryRepository.save(Category.builder()
                .userId(otherUserId)
                .name("Другая категория")
                .budget(BigDecimal.ZERO)
                .build());

        // Расходы другого пользователя за предыдущий год
        for (int month = 1; month <= 12; month++) {
            expenseRepository.save(Expense.builder()
                    .userId(otherUserId)
                    .category(otherCategory)
                    .amount(new BigDecimal("200000.00"))
                    .date(LocalDate.of(2025, month, 10))
                    .build());
        }

        // У нашего пользователя нет расходов за предыдущий год => инфляция нулевая
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("100000.00"))
                .date(LocalDate.of(2026, 1, 10))
                .build());

        String requestBody = buildRequest(userId, 2026);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.currentValue", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.yearlyAverage", comparesEqualTo(0)));
    }

    @Test
    @DisplayName("Должен корректно рассчитать дефляцию (расходы текущего года ниже предыдущего)")
    void shouldCalculateDeflationCorrectly() throws Exception {
        // Предыдущий год (2025): 12 месяцев по 100 000 = 1 200 000, среднемесячные = 100 000
        for (int month = 1; month <= 12; month++) {
            expenseRepository.save(Expense.builder()
                    .userId(userId)
                    .category(category)
                    .amount(new BigDecimal("100000.00"))
                    .date(LocalDate.of(2025, month, 15))
                    .build());
        }

        // Текущий год (2026): Январь = 80 000 (ниже среднего)
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("80000.00"))
                .date(LocalDate.of(2026, 1, 10))
                .build());

        String requestBody = buildRequest(userId, 2026);

        // Январь: avg = 80000, инфляция = (80000 - 100000) / 100000 * 100 = -20.0%
        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.monthlyData[0].amount", comparesEqualTo(-20.0)));
    }

    private String buildRequest(UUID reqUserId, int year) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "year": %d
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), year);
    }
}
