package pyc.lopatuxin.investment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pyc.lopatuxin.investment.AbstractIntegrationTest;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@DisplayName("Интеграционные тесты TransactionController")
class TransactionControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/investment/transactions";

    private UUID userId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        positionRepository.deleteAll();
        securityRepository.deleteAll();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Создать сделку BUY — вернуть 201 с ticker SBER")
    void shouldCreateTransactionAndReturn201() throws Exception {
        String body = buildCreateRequest(userId, "SBER", "BUY", "10", "250.00");

        mockMvc.perform(post(BASE_URL)
                        .content(body)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is(201)))
                .andExpect(jsonPath("$.body", notNullValue()))
                .andExpect(jsonPath("$.body.ticker", is("SBER")));
    }

    @Test
    @DisplayName("POST /list — список возвращает 1 сделку")
    void shouldReturnOneTransactionInList() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(buildCreateRequest(userId, "SBER", "BUY", "10", "250.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        String listBody = buildListRequest(userId, null);

        mockMvc.perform(post(BASE_URL + "/list")
                        .content(listBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.body", hasSize(1)));
    }

    @Test
    @DisplayName("POST /delete — удалить сделку, затем список пуст")
    void shouldDeleteTransactionAndReturnEmptyList() throws Exception {
        String createResponse = mockMvc.perform(post(BASE_URL)
                        .content(buildCreateRequest(userId, "SBER", "BUY", "10", "250.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract id from response
        String txId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.body.id");

        String deleteBody = buildDeleteRequest(userId, UUID.fromString(txId));

        mockMvc.perform(post(BASE_URL + "/delete")
                        .content(deleteBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)));

        mockMvc.perform(post(BASE_URL + "/list")
                        .content(buildListRequest(userId, null))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body", hasSize(0)));
    }

    @Test
    @DisplayName("POST /delete — чужая транзакция возвращает 404")
    void shouldReturn404WhenDeletingAnotherUserTransaction() throws Exception {
        // Create transaction under userId
        String createResponse = mockMvc.perform(post(BASE_URL)
                        .content(buildCreateRequest(userId, "SBER", "BUY", "10", "250.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String txId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.body.id");

        // Attempt delete from a different user
        UUID otherUserId = UUID.randomUUID();
        mockMvc.perform(post(BASE_URL + "/delete")
                        .content(buildDeleteRequest(otherUserId, UUID.fromString(txId)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("POST /delete — несуществующая транзакция возвращает 404")
    void shouldReturn404WhenDeletingNonExistentTransaction() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(post(BASE_URL + "/delete")
                        .content(buildDeleteRequest(userId, randomId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("POST — SELL больше чем есть → 409 Conflict")
    void shouldReturn409WhenSellExceedsAvailableQuantity() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(buildCreateRequest(userId, "GAZP", "BUY", "5", "150.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(BASE_URL)
                        .content(buildCreateRequest(userId, "GAZP", "SELL", "10", "160.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("Insufficient shares for SELL")));
    }

    private String buildCreateRequest(UUID reqUserId, String ticker, String type, String quantity, String price) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "ticker": "%s",
                    "type": "%s",
                    "securityType": "STOCK",
                    "quantity": %s,
                    "price": %s,
                    "executedAt": "2026-01-15T10:00:00Z"
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), ticker, type, quantity, price);
    }

    private String buildListRequest(UUID reqUserId, String ticker) {
        String tickerField = ticker != null ? "\"ticker\": \"%s\"".formatted(ticker) : "";
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    %s
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), tickerField);
    }

    private String buildDeleteRequest(UUID reqUserId, UUID id) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "id": "%s"
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), id);
    }
}
