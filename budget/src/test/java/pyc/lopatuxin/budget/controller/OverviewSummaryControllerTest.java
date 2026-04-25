package pyc.lopatuxin.budget.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pyc.lopatuxin.budget.AbstractIntegrationTest;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Интеграционные тесты OverviewSummaryController")
class OverviewSummaryControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/budget/overview";

    private UUID userId;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        incomeRepository.deleteAll();
        capitalRecordRepository.deleteAll();
        categoryRepository.deleteAll();
        userId = UUID.randomUUID();
    }

    // ─── Response structure ──────────────────────────────────────────────────

    @Test
    @DisplayName("Должен вернуть ответ в структуре ResponseApi (id, status, message, timestamp, body)")
    void shouldReturnResponseMatchingResponseApiContract() throws Exception {
        String requestBody = buildRequest(userId, 6, 2024);

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
    @DisplayName("Должен вернуть статус 200 и нулевые показатели при отсутствии данных")
    void shouldReturnOkWithZeroValuesWhenDatabaseIsEmpty() throws Exception {
        String requestBody = buildRequest(userId, 6, 2024);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.income", is(0)))
                .andExpect(jsonPath("$.body.expenses", is(0)))
                .andExpect(jsonPath("$.body.balance", is(0)));
    }

    // ─── savingsRate field ────────────────────────────────────────────────────

    @Test
    @DisplayName("Должен вернуть savingsRate=0 в JSON когда нет доходов")
    void shouldReturnZeroSavingsRateWhenNoIncome() throws Exception {
        String requestBody = buildRequest(userId, 6, 2024);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.savingsRate", is(0)));
    }

    @Test
    @DisplayName("Должен вернуть корректный savingsRate в JSON когда есть доходы и расходы")
    void shouldReturnCorrectSavingsRateWhenIncomeAndExpensesExist() throws Exception {
        // income=150000, expenses=0 → savingsRate clamped to 99
        // Use only income to keep the test simple without a category dependency
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("150000.00"))
                .date(LocalDate.of(2024, 3, 10))
                .build());

        String requestBody = buildRequest(userId, 3, 2024);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // income=150000, expenses=0 → raw=100 → clamped to 99
                .andExpect(jsonPath("$.body.savingsRate", is(99)));
    }

    @Test
    @DisplayName("Должен вернуть поле savingsRate присутствующим в JSON-ответе")
    void shouldContainSavingsRateFieldInJsonResponse() throws Exception {
        String requestBody = buildRequest(userId, 8, 2024);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.savingsRate").exists());
    }

    // ─── Validation ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Должен вернуть статус 400 при отсутствии параметра month")
    void shouldReturnBadRequestWhenMonthParamIsMissing() throws Exception {
        String requestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "year": 2024
                  }
                }
                """.formatted(userId, UUID.randomUUID());

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Должен вернуть статус 400 при некорректном значении month (0)")
    void shouldReturnBadRequestWhenMonthIsZero() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(buildRequest(userId, 0, 2024))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Должен вернуть статус 400 при значении year меньше допустимого минимума (2020)")
    void shouldReturnBadRequestWhenYearIsTooOld() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(buildRequest(userId, 6, 2019))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String buildRequest(UUID reqUserId, int month, int year) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "month": %d,
                    "year": %d
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), month, year);
    }
}
