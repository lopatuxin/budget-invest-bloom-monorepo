package pyc.lopatuxin.budget.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pyc.lopatuxin.budget.AbstractIntegrationTest;
import pyc.lopatuxin.budget.entity.Category;
import pyc.lopatuxin.budget.entity.Expense;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the analytics page endpoint. Uses a year (2024) safely in the past
 * relative to the test environment's clock, so every month of it is complete regardless of when
 * the suite runs — matching the convention already used by {@code BudgetSummaryControllerTest}.
 */
@DisplayName("Интеграционные тесты AnalyticsPageController")
class AnalyticsPageControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/budget/analytics";
    private static final int YEAR = 2024;
    private static final int PREVIOUS_YEAR = 2023;

    private UUID userId;
    private Category groceries;
    private Category systemInvestCategory;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        incomeRepository.deleteAll();
        categoryRepository.deleteAll();
        userId = UUID.randomUUID();

        groceries = categoryRepository.save(Category.builder()
                .userId(userId)
                .name("Продукты")
                .emoji("🛒")
                .budget(new BigDecimal("30000.00"))
                .build());

        systemInvestCategory = categoryRepository.save(Category.builder()
                .userId(userId)
                .name("Инвестиции")
                .emoji("💎")
                .budget(BigDecimal.ZERO)
                .system(true)
                .build());
    }

    @Test
    @DisplayName("Должен вернуть ответ в структуре ResponseApi с полными разделами (months из 12 элементов)")
    void shouldReturnResponseMatchingResponseApiContract() throws Exception {
        expenseRepository.save(expense(groceries, "15000.00", LocalDate.of(YEAR, 3, 10)));
        incomeRepository.save(income("120000.00", LocalDate.of(YEAR, 3, 5)));

        mockMvc.perform(post(BASE_URL)
                        .content(buildRequest(userId, YEAR))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.body.year", is(YEAR)))
                .andExpect(jsonPath("$.body.previousYear", is(PREVIOUS_YEAR)))
                .andExpect(jsonPath("$.body.expenses.months", hasSize(12)))
                .andExpect(jsonPath("$.body.income.months", hasSize(12)))
                .andExpect(jsonPath("$.body.savings.months", hasSize(12)));
    }

    @Test
    @DisplayName("Должен вернуть статус 400 при значении year меньше допустимого минимума (1950)")
    void shouldReturnBadRequestWhenYearIsTooOld() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(buildRequest(userId, 1900))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Новый пользователь без записей должен получить нули, NO_HISTORY и пустые категории")
    void shouldReturnZeroesAndNoHistoryForNewUser() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(buildRequest(userId, YEAR))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.expenses.total", is(0.0)))
                .andExpect(jsonPath("$.body.expenses.change.status", is("NO_HISTORY")))
                .andExpect(jsonPath("$.body.expenses.average").doesNotExist())
                .andExpect(jsonPath("$.body.earliestYear").doesNotExist())
                .andExpect(jsonPath("$.body.categories", hasSize(0)))
                .andExpect(jsonPath("$.body.savingsRatePercent").doesNotExist());
    }

    @Test
    @DisplayName("Трансферные записи не должны попадать в месяцы и категории")
    void shouldExcludeTransferRecordsFromMonthsAndCategories() throws Exception {
        expenseRepository.save(expense(groceries, "20000.00", LocalDate.of(YEAR, 5, 15)));
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(systemInvestCategory)
                .amount(new BigDecimal("80000.00"))
                .date(LocalDate.of(YEAR, 5, 15))
                .isTransfer(true)
                .build());

        incomeRepository.save(income("50000.00", LocalDate.of(YEAR, 5, 15)));
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.INVESTMENTS)
                .amount(new BigDecimal("30000.00"))
                .date(LocalDate.of(YEAR, 5, 20))
                .isTransfer(true)
                .build());

        mockMvc.perform(post(BASE_URL)
                        .content(buildRequest(userId, YEAR))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Май (индекс 4): расход 20000 (трансфер-расход 80000 исключён), доход 50000 (трансфер-доход исключён).
                .andExpect(jsonPath("$.body.expenses.months[4].current", comparesEqualTo(20000.0)))
                .andExpect(jsonPath("$.body.income.months[4].current", comparesEqualTo(50000.0)))
                .andExpect(jsonPath("$.body.categories", hasSize(1)))
                .andExpect(jsonPath("$.body.categories[0].categoryName", is("Продукты")));
    }

    @Test
    @DisplayName("Категории должны нести вклад в личную инфляцию на реальных записях двух лет")
    void shouldComputeCategoryContributionsAcrossTwoYears() throws Exception {
        expenseRepository.save(expense(groceries, "24000.00", LocalDate.of(YEAR, 1, 10)));
        expenseRepository.save(expense(groceries, "22000.00", LocalDate.of(PREVIOUS_YEAR, 1, 10)));

        mockMvc.perform(post(BASE_URL)
                        .content(buildRequest(userId, YEAR))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.categories", hasSize(1)))
                .andExpect(jsonPath("$.body.categories[0].averageCurrent", comparesEqualTo(24000.0)))
                .andExpect(jsonPath("$.body.categories[0].averagePrevious", comparesEqualTo(22000.0)))
                .andExpect(jsonPath("$.body.categories[0].change.status", is("NORMAL")))
                // Единственная категория несёт всю личную инфляцию.
                .andExpect(jsonPath("$.body.categories[0].contributionPoints",
                        comparesEqualTo(9.1)))
                .andExpect(jsonPath("$.body.categories[0].sharePercent", comparesEqualTo(100.0)));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Expense expense(Category category, String amount, LocalDate date) {
        return Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal(amount))
                .date(date)
                .build();
    }

    private Income income(String amount, LocalDate date) {
        return Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal(amount))
                .date(date)
                .build();
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
