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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Интеграционные тесты OverviewPageController")
class OverviewPageControllerTest extends AbstractIntegrationTest {

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
    @DisplayName("Должен вернуть ответ в структуре ResponseApi с полями страницы обзора при пустом data")
    void shouldReturnResponseMatchingResponseApiContractWithEmptyData() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(buildRequest(userId, "{}"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.body", notNullValue()))
                .andExpect(jsonPath("$.body.asOf", notNullValue()))
                .andExpect(jsonPath("$.body.currentMonth", notNullValue()))
                .andExpect(jsonPath("$.body.capital", notNullValue()))
                .andExpect(jsonPath("$.body.capital.history", hasSize(13)))
                .andExpect(jsonPath("$.body.portfolio", notNullValue()))
                .andExpect(jsonPath("$.body.savings", notNullValue()))
                .andExpect(jsonPath("$.body.months", hasSize(12)))
                .andExpect(jsonPath("$.body.totals12m", notNullValue()));
    }

    // ─── New user, empty database ─────────────────────────────────────────────

    @Test
    @DisplayName("Должен вернуть нулевые показатели и NO_HISTORY у нового пользователя без записей")
    void shouldReturnZeroValuesWhenDatabaseIsEmpty() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(buildRequest(userId, "{}"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.capital.total", is(0.0)))
                .andExpect(jsonPath("$.body.capital.freeMoney", is(0.0)))
                .andExpect(jsonPath("$.body.capital.change.status", is("NO_HISTORY")))
                .andExpect(jsonPath("$.body.capital.yearAgo").doesNotExist())
                .andExpect(jsonPath("$.body.portfolio.available", is(true)))
                .andExpect(jsonPath("$.body.portfolio.assetsCount", is(0)))
                .andExpect(jsonPath("$.body.portfolio.pnl").doesNotExist())
                .andExpect(jsonPath("$.body.savings.rate12m").doesNotExist())
                .andExpect(jsonPath("$.body.totals12m.income.change.status", is("NO_HISTORY")))
                .andExpect(jsonPath("$.body.personalInflationPercent").doesNotExist());
    }

    // ─── Income reflected in free money and savings rate ─────────────────────

    @Test
    @DisplayName("Должен учесть доход в свободных деньгах и норме сбережений текущего месяца")
    void shouldReflectIncomeInFreeMoneyAndSavingsRate() throws Exception {
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("150000.00"))
                .date(LocalDate.now())
                .build());

        mockMvc.perform(post(BASE_URL)
                        .content(buildRequest(userId, "{}"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.capital.freeMoney", is(150000.0)))
                .andExpect(jsonPath("$.body.savings.currentMonthRate", is(99)));
    }

    // ─── Legacy request body is ignored, not rejected ─────────────────────────

    @Test
    @DisplayName("Старый запрос с month/year в data должен игнорироваться, а не приводить к 400")
    void shouldIgnoreLegacyMonthYearRequestBody() throws Exception {
        String legacyRequestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "month": 6,
                    "year": 2024
                  }
                }
                """.formatted(userId, UUID.randomUUID());

        mockMvc.perform(post(BASE_URL)
                        .content(legacyRequestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.asOf", notNullValue()));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String buildRequest(UUID reqUserId, String data) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": %s
                }
                """.formatted(reqUserId, UUID.randomUUID(), data);
    }
}
