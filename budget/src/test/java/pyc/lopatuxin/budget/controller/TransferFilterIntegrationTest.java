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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying that transfer-flagged records (isTransfer=true)
 * are correctly excluded from overview/analytics widgets.
 */
@DisplayName("Интеграционные тесты фильтрации transfer-записей")
class TransferFilterIntegrationTest extends AbstractIntegrationTest {

    private static final String OVERVIEW_URL = "/api/budget/overview";

    private UUID userId;
    private Category userCategory;
    private Category systemInvestCategory;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        incomeRepository.deleteAll();
        categoryRepository.deleteAll();

        userId = UUID.randomUUID();

        userCategory = categoryRepository.save(Category.builder()
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

    // ─── Overview: свободные деньги капитала (lifetime, non-transfer only) ────
    // The overview page no longer has a categories widget or per-month income/expenses fields
    // (redesigned into a capital page — see docs/plans/overview-page-redesign.md); transfer
    // filtering on the overview endpoint is now checked through capital.freeMoney instead,
    // which is the lifetime non-transfer income-minus-expenses total shown on that page.

    @Test
    @DisplayName("Overview capital.freeMoney: transfer-расходы не учитываются")
    void overview_freeMoney_shouldExcludeTransferExpenses() throws Exception {
        LocalDate date = LocalDate.of(2025, 5, 15);

        // Non-transfer расход 20000
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(userCategory)
                .amount(new BigDecimal("20000.00"))
                .date(date)
                .isTransfer(false)
                .build());

        // Transfer-расход 80000 — не должен входить в freeMoney
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(systemInvestCategory)
                .amount(new BigDecimal("80000.00"))
                .date(date)
                .isTransfer(true)
                .build());

        // Доход 50000 (non-transfer)
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("50000.00"))
                .date(date)
                .isTransfer(false)
                .build());

        mockMvc.perform(post(OVERVIEW_URL)
                        .content(buildOverviewRequest(userId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // freeMoney = 50000 - 20000 = 30000 (transfer-расход 80000 исключён)
                .andExpect(jsonPath("$.body.capital.freeMoney", comparesEqualTo(30000.0)));
    }

    @Test
    @DisplayName("Overview capital.freeMoney: transfer-доходы не учитываются")
    void overview_freeMoney_shouldExcludeTransferIncome() throws Exception {
        LocalDate date = LocalDate.of(2025, 6, 10);

        // Non-transfer доход 100000
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("100000.00"))
                .date(date)
                .isTransfer(false)
                .build());

        // Transfer-доход (продажа активов) 30000 — не должен входить в freeMoney
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.INVESTMENTS)
                .amount(new BigDecimal("30000.00"))
                .date(date)
                .isTransfer(true)
                .build());

        mockMvc.perform(post(OVERVIEW_URL)
                        .content(buildOverviewRequest(userId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // freeMoney = 100000 (transfer-доход 30000 исключён)
                .andExpect(jsonPath("$.body.capital.freeMoney", comparesEqualTo(100000.0)));
    }

    // ─── Analytics: помесячная разбивка сбережений исключает transfer ─────────

    @Test
    @DisplayName("Analytics: помесячная разбивка сбережений не включает transfer-записи")
    void analyticsSavings_shouldExcludeTransferFromMonthlyBreakdown() throws Exception {
        // Январь: non-transfer доход 80000, transfer-доход 15000, non-transfer расход 30000, transfer-расход 50000
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("80000.00"))
                .date(LocalDate.of(2024, 1, 10))
                .isTransfer(false)
                .build());
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.INVESTMENTS)
                .amount(new BigDecimal("15000.00"))
                .date(LocalDate.of(2024, 1, 20))
                .isTransfer(true)
                .build());
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(userCategory)
                .amount(new BigDecimal("30000.00"))
                .date(LocalDate.of(2024, 1, 5))
                .isTransfer(false)
                .build());
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(systemInvestCategory)
                .amount(new BigDecimal("50000.00"))
                .date(LocalDate.of(2024, 1, 15))
                .isTransfer(true)
                .build());

        mockMvc.perform(post("/api/budget/analytics")
                        .content(buildMetricRequest(userId, 2024))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Январь: сбережения = 80000 - 30000 = 50000 (transfer не учитываются)
                .andExpect(jsonPath("$.body.savings.months[0].current", comparesEqualTo(50000.0)));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String buildOverviewRequest(UUID reqUserId) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {}
                }
                """.formatted(reqUserId, UUID.randomUUID());
    }

    private String buildMetricRequest(UUID reqUserId, int year) {
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
