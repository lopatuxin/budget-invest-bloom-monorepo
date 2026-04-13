package pyc.lopatuxin.budget.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pyc.lopatuxin.budget.AbstractIntegrationTest;

import java.util.UUID;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Интеграционные тесты IncomeController")
class IncomeControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/budget/incomes";

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
    @DisplayName("Должен создать доход и вернуть статус 201 с корректным телом ответа")
    void shouldCreateIncomeAndReturn201WithCorrectBody() throws Exception {
        String requestBody = buildRequest(userId, "SALARY", "50000.00", "Зарплата за апрель", "2026-04-13");

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is(201)))
                .andExpect(jsonPath("$.message", is("Доход успешно добавлен")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.body.id", notNullValue()))
                .andExpect(jsonPath("$.body.source", is("SALARY")))
                .andExpect(jsonPath("$.body.sourceName", is("Зарплата")))
                .andExpect(jsonPath("$.body.amount", comparesEqualTo(50000.00)))
                .andExpect(jsonPath("$.body.description", is("Зарплата за апрель")))
                .andExpect(jsonPath("$.body.date", is("2026-04-13")));
    }

    @Test
    @DisplayName("Должен создать доход без даты — используется текущая дата")
    void shouldCreateIncomeWithoutDateUsingCurrentDate() throws Exception {
        String requestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "source": "FREELANCE",
                    "amount": 12000.00,
                    "description": "Без даты"
                  }
                }
                """.formatted(userId, UUID.randomUUID());

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body.date", notNullValue()))
                .andExpect(jsonPath("$.body.amount", comparesEqualTo(12000.00)));
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
                    "source": "SALARY"
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
    @DisplayName("Должен вернуть 400 при отсутствии source (null)")
    void shouldReturn400WhenSourceIsNull() throws Exception {
        String requestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "amount": 10000.00
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
    @DisplayName("Должен вернуть 400 при отрицательной сумме дохода")
    void shouldReturn400WhenAmountIsNegative() throws Exception {
        String requestBody = buildRequest(userId, "SALARY", "-100.00", "Отрицательная сумма", "2026-04-13");

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    private String buildRequest(UUID reqUserId, String source, String amount, String description, String date) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "source": "%s",
                    "amount": %s,
                    "description": "%s",
                    "date": "%s"
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), source, amount, description, date);
    }
}
