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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying that transfer-flagged records (isTransfer=true)
 * are correctly excluded from overview/balance-metric widgets but included in lifetime balance.
 */
@DisplayName("Интеграционные тесты фильтрации transfer-записей")
class TransferFilterIntegrationTest extends AbstractIntegrationTest {

    private static final String OVERVIEW_URL = "/api/budget/overview";
    private static final String LIFETIME_BALANCE_URL = "/api/budget/balance/lifetime";

    private UUID userId;
    private Category userCategory;
    private Category systemInvestCategory;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        incomeRepository.deleteAll();
        capitalRecordRepository.deleteAll();
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

    // ─── Overview: виджет «Основные категории трат» ───────────────────────────

    @Test
    @DisplayName("Overview categories: transfer-расходы исключаются из виджета топ-категорий")
    void overview_categoriesWidget_shouldExcludeTransferExpenses() throws Exception {
        LocalDate date = LocalDate.of(2025, 3, 10);

        // Обычный расход — должен попасть в виджет
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(userCategory)
                .amount(new BigDecimal("15000.00"))
                .date(date)
                .isTransfer(false)
                .build());

        // Transfer-расход (покупка активов) — не должен попасть в виджет
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(systemInvestCategory)
                .amount(new BigDecimal("50000.00"))
                .date(date)
                .isTransfer(true)
                .build());

        mockMvc.perform(post(OVERVIEW_URL)
                        .content(buildOverviewRequest(userId, 3, 2025))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Виджет categories содержит только обычный расход (категория «Продукты»)
                .andExpect(jsonPath("$.body.categories", hasSize(1)))
                .andExpect(jsonPath("$.body.categories[0].name", is("Продукты")));
    }

    @Test
    @DisplayName("Overview categories: если все расходы transfer — виджет пустой")
    void overview_categoriesWidget_shouldBeEmptyWhenAllExpensesAreTransfer() throws Exception {
        LocalDate date = LocalDate.of(2025, 4, 5);

        // Только transfer-расходы
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(systemInvestCategory)
                .amount(new BigDecimal("100000.00"))
                .date(date)
                .isTransfer(true)
                .build());

        mockMvc.perform(post(OVERVIEW_URL)
                        .content(buildOverviewRequest(userId, 4, 2025))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.categories", empty()));
    }

    // ─── Overview: суммарные расходы месяца (expenses) ────────────────────────

    @Test
    @DisplayName("Overview expenses: transfer-расходы не учитываются в общей сумме расходов месяца")
    void overview_expenses_shouldExcludeTransferFromMonthlyTotal() throws Exception {
        LocalDate date = LocalDate.of(2025, 5, 15);

        // Non-transfer расход 20000
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(userCategory)
                .amount(new BigDecimal("20000.00"))
                .date(date)
                .isTransfer(false)
                .build());

        // Transfer-расход 80000 — не должен входить в expenses
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
                        .content(buildOverviewRequest(userId, 5, 2025))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // expenses = 20000 (без transfer)
                .andExpect(jsonPath("$.body.expenses", comparesEqualTo(20000.0)))
                // income = 50000 (без transfer)
                .andExpect(jsonPath("$.body.income", comparesEqualTo(50000.0)))
                // balance = 50000 - 20000 = 30000
                .andExpect(jsonPath("$.body.balance", comparesEqualTo(30000.0)));
    }

    @Test
    @DisplayName("Overview income: transfer-доходы не учитываются в общей сумме доходов месяца")
    void overview_income_shouldExcludeTransferFromMonthlyTotal() throws Exception {
        LocalDate date = LocalDate.of(2025, 6, 10);

        // Non-transfer доход 100000
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("100000.00"))
                .date(date)
                .isTransfer(false)
                .build());

        // Transfer-доход (продажа активов) 30000 — не должен входить в income
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.INVESTMENTS)
                .amount(new BigDecimal("30000.00"))
                .date(date)
                .isTransfer(true)
                .build());

        mockMvc.perform(post(OVERVIEW_URL)
                        .content(buildOverviewRequest(userId, 6, 2025))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.income", comparesEqualTo(100000.0)));
    }

    // ─── BalanceService lifetime: transfer-записи ДОЛЖНЫ учитываться ──────────

    @Test
    @DisplayName("Lifetime balance: transfer-расходы и доходы учитываются в суммах за всё время")
    void lifetimeBalance_shouldIncludeTransferEntries() throws Exception {
        // Non-transfer расход 30000
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(userCategory)
                .amount(new BigDecimal("30000.00"))
                .date(LocalDate.of(2025, 1, 10))
                .isTransfer(false)
                .build());

        // Transfer-расход (покупка активов) 50000 — должен войти в totalExpense
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(systemInvestCategory)
                .amount(new BigDecimal("50000.00"))
                .date(LocalDate.of(2025, 1, 15))
                .isTransfer(true)
                .build());

        // Non-transfer доход 200000
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("200000.00"))
                .date(LocalDate.of(2025, 1, 5))
                .isTransfer(false)
                .build());

        // Transfer-доход (продажа активов) 40000 — должен войти в totalIncome
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.INVESTMENTS)
                .amount(new BigDecimal("40000.00"))
                .date(LocalDate.of(2025, 1, 20))
                .isTransfer(true)
                .build());

        mockMvc.perform(post(LIFETIME_BALANCE_URL)
                        .content(buildLifetimeRequest(userId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // totalExpense = 30000 + 50000 = 80000 (включая transfer)
                .andExpect(jsonPath("$.body.totalExpense", comparesEqualTo(80000.0)))
                // totalIncome = 200000 + 40000 = 240000 (включая transfer)
                .andExpect(jsonPath("$.body.totalIncome", comparesEqualTo(240000.0)))
                // freeCapital = 240000 - 80000 = 160000
                .andExpect(jsonPath("$.body.freeCapital", comparesEqualTo(160000.0)));
    }

    @Test
    @DisplayName("Lifetime balance: только transfer-записи — все суммируются корректно")
    void lifetimeBalance_onlyTransferEntries_shouldSumAll() throws Exception {
        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(systemInvestCategory)
                .amount(new BigDecimal("100000.00"))
                .date(LocalDate.of(2025, 2, 1))
                .isTransfer(true)
                .build());

        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.INVESTMENTS)
                .amount(new BigDecimal("120000.00"))
                .date(LocalDate.of(2025, 2, 5))
                .isTransfer(true)
                .build());

        mockMvc.perform(post(LIFETIME_BALANCE_URL)
                        .content(buildLifetimeRequest(userId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.totalExpense", comparesEqualTo(100000.0)))
                .andExpect(jsonPath("$.body.totalIncome", comparesEqualTo(120000.0)))
                .andExpect(jsonPath("$.body.freeCapital", comparesEqualTo(20000.0)));
    }

    // ─── BalanceMetricService: помесячная разбивка исключает transfer ─────────

    @Test
    @DisplayName("BalanceMetric: помесячная разбивка не включает transfer-записи")
    void balanceMetric_shouldExcludeTransferFromMonthlyBreakdown() throws Exception {
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

        mockMvc.perform(post("/api/budget/metric/balance")
                        .content(buildMetricRequest(userId, 2024))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Январь: баланс = 80000 - 30000 = 50000 (transfer не учитываются)
                .andExpect(jsonPath("$.body.monthlyData[0].amount", comparesEqualTo(50000.0)));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String buildOverviewRequest(UUID reqUserId, int month, int year) {
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

    private String buildLifetimeRequest(UUID reqUserId) {
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
