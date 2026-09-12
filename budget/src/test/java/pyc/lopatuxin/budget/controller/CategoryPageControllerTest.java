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

@DisplayName("Интеграционные тесты CategoryPageController")
class CategoryPageControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/budget/categories/page";

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
                .name("Продукты")
                .emoji("🛒")
                .budget(new BigDecimal("30000.00"))
                .build());
    }

    @Test
    @DisplayName("Должен вернуть 200 с обёрткой ResponseApi и всеми полями страницы категории")
    void shouldReturn200WithAllFields() throws Exception {
        expenseRepository.save(expense(new BigDecimal("1240.00"), LocalDate.of(2026, 9, 5), false));

        String requestBody = buildRequest(userId, "Продукты", 9, 2026);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.body.category.id", is(category.getId().toString())))
                .andExpect(jsonPath("$.body.category.name", is("Продукты")))
                .andExpect(jsonPath("$.body.category.emoji", is("🛒")))
                .andExpect(jsonPath("$.body.category.system", is(false)))
                .andExpect(jsonPath("$.body.period.month", is(9)))
                .andExpect(jsonPath("$.body.period.year", is(2026)))
                .andExpect(jsonPath("$.body.daysInMonth", is(30)))
                .andExpect(jsonPath("$.body.spent", comparesEqualTo(1240.00)))
                .andExpect(jsonPath("$.body.norm.status", is("NO_HISTORY")))
                .andExpect(jsonPath("$.body.normMonthsCounted", is(0)))
                .andExpect(jsonPath("$.body.operationsCount", is(1)))
                .andExpect(jsonPath("$.body.averageCheck", comparesEqualTo(1240.00)))
                .andExpect(jsonPath("$.body.largestAmount", comparesEqualTo(1240.00)))
                .andExpect(jsonPath("$.body.months", hasSize(12)))
                .andExpect(jsonPath("$.body.months[11].month", is(9)))
                .andExpect(jsonPath("$.body.months[11].year", is(2026)))
                .andExpect(jsonPath("$.body.months[11].amount", comparesEqualTo(1240.00)))
                .andExpect(jsonPath("$.body.operations", hasSize(1)))
                .andExpect(jsonPath("$.body.operations[0].kind", is("EXPENSE")))
                .andExpect(jsonPath("$.body.operations[0].categoryId", is(category.getId().toString())))
                .andExpect(jsonPath("$.body.operations[0].categoryName", is("Продукты")))
                .andExpect(jsonPath("$.body.operations[0].categoryEmoji", is("🛒")));
    }

    @Test
    @DisplayName("Должен вернуть 404 для несуществующего названия категории")
    void shouldReturn404WhenCategoryNotFound() throws Exception {
        String requestBody = buildRequest(userId, "Несуществующая", 9, 2026);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Категория не найдена")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDataProvider")
    @DisplayName("Должен вернуть 400 при невалидных параметрах запроса")
    void shouldReturn400WhenDataIsInvalid(String scenario, String requestBody) throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    static Stream<Arguments> invalidDataProvider() {
        UUID uid = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        String userBlock = """
                "user": {"userId": "%s", "email": "test@example.com", "role": "USER", "sessionId": "%s"}""".formatted(uid, sid);

        return Stream.of(
                Arguments.of("month отсутствует (null)",
                        "{%s, \"data\": {\"categoryName\": \"Еда\", \"year\": 2026}}".formatted(userBlock)),
                Arguments.of("month больше 12",
                        "{%s, \"data\": {\"categoryName\": \"Еда\", \"year\": 2026, \"month\": 13}}".formatted(userBlock)),
                Arguments.of("categoryName пустой (blank)",
                        "{%s, \"data\": {\"categoryName\": \"   \", \"year\": 2026, \"month\": 9}}".formatted(userBlock))
        );
    }

    @Test
    @DisplayName("Трансферные записи не должны попадать ни в spent, ни в operations, ни в months")
    void shouldExcludeTransferExpenses() throws Exception {
        expenseRepository.save(expense(new BigDecimal("1000.00"), LocalDate.of(2026, 9, 5), false));
        expenseRepository.save(expense(new BigDecimal("50000.00"), LocalDate.of(2026, 9, 10), true));

        String requestBody = buildRequest(userId, "Продукты", 9, 2026);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.spent", comparesEqualTo(1000.00)))
                .andExpect(jsonPath("$.body.operations", hasSize(1)))
                .andExpect(jsonPath("$.body.months[11].amount", comparesEqualTo(1000.00)));
    }

    @Test
    @DisplayName("Операции должны быть отсортированы по дате, затем по времени создания, по убыванию")
    void shouldOrderOperationsByDateThenCreatedAtDescending() throws Exception {
        Expense earlierSameDayLater = expenseRepository.save(expense(new BigDecimal("100.00"), LocalDate.of(2026, 9, 5), false));
        Thread.sleep(5);
        Expense sameDayEarlierCreated = expenseRepository.save(expense(new BigDecimal("200.00"), LocalDate.of(2026, 9, 5), false));
        Expense laterDate = expenseRepository.save(expense(new BigDecimal("300.00"), LocalDate.of(2026, 9, 10), false));

        String requestBody = buildRequest(userId, "Продукты", 9, 2026);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.operations", hasSize(3)))
                .andExpect(jsonPath("$.body.operations[0].id", is(laterDate.getId().toString())))
                .andExpect(jsonPath("$.body.operations[1].id", is(sameDayEarlierCreated.getId().toString())))
                .andExpect(jsonPath("$.body.operations[2].id", is(earlierSameDayLater.getId().toString())));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Expense expense(BigDecimal amount, LocalDate date, boolean isTransfer) {
        return Expense.builder()
                .userId(userId)
                .category(category)
                .amount(amount)
                .description("Покупка")
                .date(date)
                .isTransfer(isTransfer)
                .build();
    }

    private String buildRequest(UUID reqUserId, String categoryName, int month, int year) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "categoryName": "%s",
                    "month": %d,
                    "year": %d
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), categoryName, month, year);
    }
}
