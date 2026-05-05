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
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;

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

@DisplayName("Интеграционные тесты BalanceMetricController")
class BalanceMetricControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/budget/metric/balance";

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
    @DisplayName("Должен вернуть статус 200 и корректную метрику баланса при наличии доходов и расходов")
    void shouldReturnOkWithCorrectBalanceWhenDataExists() throws Exception {
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("120000.00"))
                .date(LocalDate.of(2025,1, 15))
                .build());

        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("150000.00"))
                .date(LocalDate.of(2025,3, 15))
                .build());

        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("60000.00"))
                .date(LocalDate.of(2025,1, 20))
                .build());

        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("80000.00"))
                .date(LocalDate.of(2025,3, 20))
                .build());

        String requestBody = buildRequest(userId, 2025);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Метрика баланса успешно получена")))
                .andExpect(jsonPath("$.body.year", is(2025)))
                .andExpect(jsonPath("$.body.monthlyData", hasSize(12)))
                // Январь: 120000 - 60000 = 60000
                .andExpect(jsonPath("$.body.monthlyData[0].month", is(1)))
                .andExpect(jsonPath("$.body.monthlyData[0].monthName", is("Янв")))
                .andExpect(jsonPath("$.body.monthlyData[0].amount", comparesEqualTo(60000.00)))
                // Март: 150000 - 80000 = 70000
                .andExpect(jsonPath("$.body.monthlyData[2].amount", comparesEqualTo(70000.00)))
                // Февраль: нет данных = 0
                .andExpect(jsonPath("$.body.monthlyData[1].amount", comparesEqualTo(0)))
                // currentValue = последний ненулевой (Март = 70000)
                .andExpect(jsonPath("$.body.currentValue", comparesEqualTo(70000.00)))
                // previousValue = предпоследний (Январь = 60000)
                .andExpect(jsonPath("$.body.previousValue", comparesEqualTo(60000.00)))
                .andExpect(jsonPath("$.body.yearlyMax", comparesEqualTo(70000.00)))
                .andExpect(jsonPath("$.body.yearlyAverage", notNullValue()))
                .andExpect(jsonPath("$.body.changePercent", notNullValue()));
    }

    @Test
    @DisplayName("Должен вернуть ответ в структуре ResponseApi (id, status, message, timestamp, body)")
    void shouldReturnResponseMatchingResponseApiContract() throws Exception {
        String requestBody = buildRequest(userId, 2025);

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
    @DisplayName("Должен вернуть статус 200 и нулевые показатели при отсутствии данных за указанный год")
    void shouldReturnOkWithZeroValuesWhenNoData() throws Exception {
        String requestBody = buildRequest(userId, 2025);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.year", is(2025)))
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
    @DisplayName("Должен не учитывать данные другого пользователя")
    void shouldNotIncludeOtherUserData() throws Exception {
        UUID otherUserId = UUID.randomUUID();

        Category otherCategory = categoryRepository.save(Category.builder()
                .userId(otherUserId)
                .name("Другая категория")
                .budget(BigDecimal.ZERO)
                .build());

        incomeRepository.save(Income.builder()
                .userId(otherUserId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("500000.00"))
                .date(LocalDate.of(2025,1, 10))
                .build());

        expenseRepository.save(Expense.builder()
                .userId(otherUserId)
                .category(otherCategory)
                .amount(new BigDecimal("100000.00"))
                .date(LocalDate.of(2025,1, 10))
                .build());

        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("100000.00"))
                .date(LocalDate.of(2025,1, 15))
                .build());

        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("30000.00"))
                .date(LocalDate.of(2025,1, 15))
                .build());

        String requestBody = buildRequest(userId, 2025);

        // Баланс userId: 100000 - 30000 = 70000 (данные otherUserId не учитываются)
        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.currentValue", comparesEqualTo(70000.00)))
                .andExpect(jsonPath("$.body.monthlyData[0].amount", comparesEqualTo(70000.00)));
    }

    @Test
    @DisplayName("Должен суммировать несколько записей за один месяц при расчёте баланса")
    void shouldSumMultipleEntriesInSameMonth() throws Exception {
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("80000.00"))
                .date(LocalDate.of(2025,5, 1))
                .build());

        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("20000.00"))
                .date(LocalDate.of(2025,5, 20))
                .build());

        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("40000.00"))
                .date(LocalDate.of(2025,5, 5))
                .build());

        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("10000.00"))
                .date(LocalDate.of(2025,5, 25))
                .build());

        String requestBody = buildRequest(userId, 2025);

        // Май: (80000 + 20000) - (40000 + 10000) = 100000 - 50000 = 50000
        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.monthlyData[4].amount", comparesEqualTo(50000.00)))
                .andExpect(jsonPath("$.body.currentValue", comparesEqualTo(50000.00)));
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
