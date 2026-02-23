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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Интеграционные тесты BudgetSummaryController")
class BudgetSummaryControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/summary";

    private UUID userId;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        incomeRepository.deleteAll();
        capitalRecordRepository.deleteAll();
        categoryRepository.deleteAll();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Должен вернуть статус 200 и корректную структуру ответа при наличии данных в БД")
    void shouldReturnOkWithCorrectBodyWhenDataExists() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .userId(userId)
                .name("Продукты")
                .emoji("🛒")
                .budget(new BigDecimal("30000.00"))
                .build());

        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("15000.00"))
                .date(LocalDate.of(2024, 12, 10))
                .build());

        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("120000.00"))
                .date(LocalDate.of(2024, 12, 5))
                .build());

        mockMvc.perform(get(BASE_URL)
                        .param("userId", userId.toString())
                        .param("month", "12")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Сводка бюджета успешно получена")))
                .andExpect(jsonPath("$.body.income").exists())
                .andExpect(jsonPath("$.body.expenses").exists())
                .andExpect(jsonPath("$.body.categories", hasSize(1)));
    }

    @Test
    @DisplayName("Должен вернуть статус 200 и нулевые показатели при отсутствии данных")
    void shouldReturnOkWithZeroValuesWhenDatabaseIsEmpty() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("userId", userId.toString())
                        .param("month", "6")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.income", is(0)))
                .andExpect(jsonPath("$.body.expenses", is(0)))
                .andExpect(jsonPath("$.body.balance", is(0)))
                .andExpect(jsonPath("$.body.categories", hasSize(0)));
    }

    @Test
    @DisplayName("Должен вернуть ответ в структуре ResponseApi (id, status, message, timestamp, body)")
    void shouldReturnResponseMatchingResponseApiContract() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("userId", userId.toString())
                        .param("month", "6")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.body", notNullValue()));
    }

    @Test
    @DisplayName("Должен вернуть статус 400 при отсутствии параметра month")
    void shouldReturnBadRequestWhenMonthParamIsMissing() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("userId", userId.toString())
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Должен вернуть статус 400 при отсутствии параметра userId")
    void shouldReturnBadRequestWhenUserIdParamIsMissing() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("month", "6")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @MethodSource("invalidMonthValues")
    @DisplayName("Должен вернуть статус 400 при некорректном значении month")
    void shouldReturnBadRequestWhenMonthIsOutOfRange(String month) throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("userId", userId.toString())
                        .param("month", month)
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    private static Stream<Arguments> invalidMonthValues() {
        return Stream.of(
                Arguments.of("0"),
                Arguments.of("13")
        );
    }

    @Test
    @DisplayName("Должен вернуть статус 400 при значении year меньше допустимого минимума (2020)")
    void shouldReturnBadRequestWhenYearIsTooOld() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("userId", userId.toString())
                        .param("month", "6")
                        .param("year", "2019")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}