package pyc.lopatuxin.budget.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pyc.lopatuxin.budget.AbstractIntegrationTest;
import pyc.lopatuxin.budget.entity.Category;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Интеграционные тесты ExpenseController")
class ExpenseControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/budget/expenses";

    private UUID userId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        incomeRepository.deleteAll();
        capitalRecordRepository.deleteAll();
        categoryRepository.deleteAll();
        userId = UUID.randomUUID();

        Category category = categoryRepository.save(Category.builder()
                .userId(userId)
                .name("Продукты")
                .budget(new BigDecimal("30000.00"))
                .build());
        categoryId = category.getId();
    }

    @Test
    @DisplayName("Должен создать расход и вернуть статус 201 с корректным телом ответа")
    void shouldCreateExpenseAndReturn201WithCorrectBody() throws Exception {
        String requestBody = buildRequest(userId, categoryId, "1500.00", "Покупка продуктов", "2026-04-06");

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.message", is("Расход успешно добавлен")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.body.id", notNullValue()))
                .andExpect(jsonPath("$.body.categoryId", is(categoryId.toString())))
                .andExpect(jsonPath("$.body.categoryName", is("Продукты")))
                .andExpect(jsonPath("$.body.amount", comparesEqualTo(1500.00)))
                .andExpect(jsonPath("$.body.description", is("Покупка продуктов")))
                .andExpect(jsonPath("$.body.date", is("2026-04-06")));
    }

    @Test
    @DisplayName("Должен создать расход без даты — используется текущая дата")
    void shouldCreateExpenseWithoutDateUsingCurrentDate() throws Exception {
        String requestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "categoryId": "%s",
                    "amount": 750.50,
                    "description": "Без даты"
                  }
                }
                """.formatted(userId, UUID.randomUUID(), categoryId);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body.date", notNullValue()))
                .andExpect(jsonPath("$.body.amount", comparesEqualTo(750.50)));
    }

    @Test
    @DisplayName("Должен вернуть 400 при отсутствии amount (null)")
    void shouldReturn400WhenAmountIsNull() throws Exception {
        String requestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "categoryId": "%s"
                  }
                }
                """.formatted(userId, UUID.randomUUID(), categoryId);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Должен вернуть 400 при отсутствии categoryId (null)")
    void shouldReturn400WhenCategoryIdIsNull() throws Exception {
        String requestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "amount": 1000.00
                  }
                }
                """.formatted(userId, UUID.randomUUID());

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Должен вернуть 404 при несуществующей категории")
    void shouldReturn404WhenCategoryNotFound() throws Exception {
        UUID nonExistentCategoryId = UUID.randomUUID();
        String requestBody = buildRequest(userId, nonExistentCategoryId, "500.00", "Тестовый расход", "2026-04-06");

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Категория не найдена")));
    }

    @Test
    @DisplayName("Должен вернуть 400 при отрицательной сумме расхода")
    void shouldReturn400WhenAmountIsNegative() throws Exception {
        String requestBody = buildRequest(userId, categoryId, "-100.00", "Отрицательная сумма", "2026-04-06");

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Должен вернуть 404 при попытке использовать категорию другого пользователя")
    void shouldReturn404WhenCategoryBelongsToAnotherUser() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        String requestBody = buildRequest(otherUserId, categoryId, "200.00", "Чужая категория", "2026-04-06");

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Категория не найдена")));
    }

    private String buildRequest(UUID reqUserId, UUID reqCategoryId, String amount, String description, String date) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "categoryId": "%s",
                    "amount": %s,
                    "description": "%s",
                    "date": "%s"
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), reqCategoryId, amount, description, date);
    }
}
