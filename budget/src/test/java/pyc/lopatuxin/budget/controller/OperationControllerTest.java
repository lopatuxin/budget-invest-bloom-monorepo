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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Интеграционные тесты OperationController")
class OperationControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/budget/operations";

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
                .budget(BigDecimal.ZERO)
                .build());
    }

    @Test
    @DisplayName("Должен вернуть ленту операций за месяц, отсортированную по дате по убыванию")
    void shouldReturnOperationsFeedSortedByDateDescending() throws Exception {
        expenseRepository.save(Expense.builder()
                .userId(userId).category(category)
                .amount(new BigDecimal("2340.00"))
                .description("Пятёрочка")
                .date(LocalDate.of(2026, 9, 5))
                .build());
        incomeRepository.save(Income.builder()
                .userId(userId)
                .source(IncomeSource.FREELANCE)
                .amount(new BigDecimal("12000.00"))
                .date(LocalDate.of(2026, 9, 18))
                .build());

        String requestBody = buildRequest(userId, 9, 2026);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Лента операций успешно получена")))
                .andExpect(jsonPath("$.body.total", is(2)))
                .andExpect(jsonPath("$.body.items", hasSize(2)))
                .andExpect(jsonPath("$.body.items[0].kind", is("INCOME")))
                .andExpect(jsonPath("$.body.items[0].date", is("2026-09-18")))
                .andExpect(jsonPath("$.body.items[0].sourceName", is("Фриланс")))
                .andExpect(jsonPath("$.body.items[1].kind", is("EXPENSE")))
                .andExpect(jsonPath("$.body.items[1].categoryName", is("Продукты")))
                .andExpect(jsonPath("$.body.items[1].categoryEmoji", is("🛒")));
    }

    @Test
    @DisplayName("Должен исключить трансферные записи из ленты")
    void shouldExcludeTransferRecordsFromFeed() throws Exception {
        expenseRepository.save(Expense.builder()
                .userId(userId).category(category)
                .amount(new BigDecimal("50000.00"))
                .date(LocalDate.of(2026, 9, 10))
                .isTransfer(true)
                .build());

        String requestBody = buildRequest(userId, 9, 2026);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.total", is(0)))
                .andExpect(jsonPath("$.body.items", hasSize(0)));
    }

    @Test
    @DisplayName("Должен вернуть пустую ленту для месяца без операций")
    void shouldReturnEmptyFeedWhenNoOperationsInMonth() throws Exception {
        String requestBody = buildRequest(userId, 1, 2026);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.total", is(0)))
                .andExpect(jsonPath("$.body.items", hasSize(0)));
    }

    @ParameterizedTest
    @MethodSource("invalidMonthValues")
    @DisplayName("Должен вернуть 400 при некорректном значении month")
    void shouldReturnBadRequestWhenMonthIsOutOfRange(int month) throws Exception {
        String requestBody = buildRequest(userId, month, 2026);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    private static Stream<Arguments> invalidMonthValues() {
        return Stream.of(Arguments.of(0), Arguments.of(13));
    }

    @Test
    @DisplayName("Должен вернуть 400 при отсутствии параметра year")
    void shouldReturnBadRequestWhenYearIsMissing() throws Exception {
        String requestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "month": 9
                  }
                }
                """.formatted(userId, UUID.randomUUID());

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

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
