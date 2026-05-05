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

@DisplayName("Интеграционные тесты CategoryAnalyticsController")
class CategoryAnalyticsControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/budget/categories/analytics";

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
    @DisplayName("Должен вернуть аналитику категории с помесячными, годовыми данными и расходами")
    void shouldReturnAnalyticsWithMonthlyYearlyDataAndExpenses() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .userId(userId)
                .name("Продукты")
                .budget(new BigDecimal("30000.00"))
                .emoji("\uD83D\uDED2")
                .build());

        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("1500.00"))
                .description("Покупка в магазине")
                .date(LocalDate.of(2025,4, 1))
                .build());

        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("2500.00"))
                .description("Покупка на рынке")
                .date(LocalDate.of(2025,4, 15))
                .build());

        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("3000.00"))
                .description("Покупка в январе")
                .date(LocalDate.of(2025,1, 10))
                .build());

        String requestBody = buildAnalyticsRequest(userId, "Продукты", 2025, 4);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.message", is("Аналитика категории получена")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.body.categoryId", is(category.getId().toString())))
                .andExpect(jsonPath("$.body.categoryName", is("Продукты")))
                .andExpect(jsonPath("$.body.emoji", is("\uD83D\uDED2")))
                .andExpect(jsonPath("$.body.budget", comparesEqualTo(30000.00)))
                .andExpect(jsonPath("$.body.monthlyData", hasSize(12)))
                .andExpect(jsonPath("$.body.monthlyData[3].month", is(4)))
                .andExpect(jsonPath("$.body.monthlyData[3].amount", comparesEqualTo(4000.00)))
                .andExpect(jsonPath("$.body.monthlyData[0].month", is(1)))
                .andExpect(jsonPath("$.body.monthlyData[0].amount", comparesEqualTo(3000.00)))
                .andExpect(jsonPath("$.body.yearlyData").isArray())
                .andExpect(jsonPath("$.body.expenses", hasSize(2)))
                .andExpect(jsonPath("$.body.totalExpenses", comparesEqualTo(4000.00)));
    }

    @Test
    @DisplayName("Должен вернуть аналитику за весь год когда месяц не указан")
    void shouldReturnAnalyticsForWholeYearWhenMonthNotSpecified() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .userId(userId)
                .name("Транспорт")
                .budget(new BigDecimal("10000.00"))
                .build());

        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("500.00"))
                .description("Метро")
                .date(LocalDate.of(2025,1, 5))
                .build());

        expenseRepository.save(Expense.builder()
                .userId(userId)
                .category(category)
                .amount(new BigDecimal("700.00"))
                .description("Такси")
                .date(LocalDate.of(2025,6, 15))
                .build());

        String requestBody = buildAnalyticsRequestWithoutMonth(userId, "Транспорт", 2025);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.expenses", hasSize(2)))
                .andExpect(jsonPath("$.body.totalExpenses", comparesEqualTo(1200.00)))
                .andExpect(jsonPath("$.body.monthlyData", hasSize(12)));
    }

    @Test
    @DisplayName("Должен вернуть пустую аналитику когда расходов нет")
    void shouldReturnEmptyAnalyticsWhenNoExpenses() throws Exception {
        categoryRepository.save(Category.builder()
                .userId(userId)
                .name("Развлечения")
                .budget(new BigDecimal("5000.00"))
                .build());

        String requestBody = buildAnalyticsRequest(userId, "Развлечения", 2025, 4);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.expenses", hasSize(0)))
                .andExpect(jsonPath("$.body.totalExpenses", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.monthlyData", hasSize(12)));
    }

    @Test
    @DisplayName("Должен вернуть 404 когда категория не найдена")
    void shouldReturn404WhenCategoryNotFound() throws Exception {
        String requestBody = buildAnalyticsRequest(userId, "Несуществующая", 2025, 4);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Категория не найдена")));
    }

    @Test
    @DisplayName("Должен вернуть 404 при запросе категории другого пользователя")
    void shouldReturn404WhenCategoryBelongsToAnotherUser() throws Exception {
        categoryRepository.save(Category.builder()
                .userId(UUID.randomUUID())
                .name("Чужая категория")
                .budget(new BigDecimal("10000.00"))
                .build());

        String requestBody = buildAnalyticsRequest(userId, "Чужая категория", 2025, 4);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAnalyticsDataProvider")
    @DisplayName("Должен вернуть 400 при невалидных данных запроса аналитики")
    void shouldReturn400WhenDataIsInvalid(String scenario, String requestBody) throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    static Stream<Arguments> invalidAnalyticsDataProvider() {
        UUID uid = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        String userBlock = """
                "user": {"userId": "%s", "email": "test@example.com", "role": "USER", "sessionId": "%s"}""".formatted(uid, sid);

        return Stream.of(
                Arguments.of("categoryName отсутствует (null)",
                        "{%s, \"data\": {\"year\": 2026}}".formatted(userBlock)),
                Arguments.of("categoryName пустой (blank)",
                        "{%s, \"data\": {\"categoryName\": \"   \", \"year\": 2026}}".formatted(userBlock)),
                Arguments.of("year отсутствует (null)",
                        "{%s, \"data\": {\"categoryName\": \"Еда\"}}".formatted(userBlock)),
                Arguments.of("year меньше 1950",
                        "{%s, \"data\": {\"categoryName\": \"Еда\", \"year\": 1949}}".formatted(userBlock)),
                Arguments.of("year больше 2100",
                        "{%s, \"data\": {\"categoryName\": \"Еда\", \"year\": 2101}}".formatted(userBlock)),
                Arguments.of("month меньше 1",
                        "{%s, \"data\": {\"categoryName\": \"Еда\", \"year\": 2026, \"month\": 0}}".formatted(userBlock)),
                Arguments.of("month больше 12",
                        "{%s, \"data\": {\"categoryName\": \"Еда\", \"year\": 2026, \"month\": 13}}".formatted(userBlock))
        );
    }

    @Test
    @DisplayName("Должен вернуть 400 при отсутствии блока user")
    void shouldReturn400WhenUserBlockIsMissing() throws Exception {
        String requestBody = """
                {
                  "data": {
                    "categoryName": "Еда",
                    "year": 2026,
                    "month": 4
                  }
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Должен вернуть 400 при отсутствии блока data")
    void shouldReturn400WhenDataBlockIsMissing() throws Exception {
        String requestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  }
                }
                """.formatted(userId, UUID.randomUUID());

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    private String buildAnalyticsRequest(UUID reqUserId, String categoryName, int year, int month) {
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
                    "year": %d,
                    "month": %d
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), categoryName, year, month);
    }

    private String buildAnalyticsRequestWithoutMonth(UUID reqUserId, String categoryName, int year) {
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
                    "year": %d
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), categoryName, year);
    }
}
