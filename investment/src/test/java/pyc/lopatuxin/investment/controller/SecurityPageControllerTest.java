package pyc.lopatuxin.investment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pyc.lopatuxin.investment.AbstractIntegrationTest;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Интеграционные тесты SecurityPageController")
class SecurityPageControllerTest extends AbstractIntegrationTest {

    private static final String PAGE_URL = "/api/investment/securities/page";
    private static final String TRANSACTIONS_URL = "/api/investment/transactions";

    private UUID userId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        positionRepository.deleteAll();
        dividendRepository.deleteAll();
        securityRepository.deleteAll();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("2 BUY SBER → POST /page → 200 с бумагой, позицией и лентой из двух событий")
    void shouldReturnSecurityPageAfterTwoBuys() throws Exception {
        mockMvc.perform(post(TRANSACTIONS_URL)
                        .content(buildCreateRequest(userId, "SBER", "10", "250.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(post(TRANSACTIONS_URL)
                        .content(buildCreateRequest(userId, "SBER", "5", "280.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(PAGE_URL)
                        .content(buildTickerRequest(userId, "SBER"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.body.security.ticker", is("SBER")))
                .andExpect(jsonPath("$.body.position.quantity", is(15.0)))
                .andExpect(jsonPath("$.body.transactionsCount", is(2)))
                .andExpect(jsonPath("$.body.buysCount", is(2)))
                .andExpect(jsonPath("$.body.sellsCount", is(0)))
                .andExpect(jsonPath("$.body.events", hasSize(2)))
                .andExpect(jsonPath("$.body.markers", hasSize(2)));
    }

    @Test
    @DisplayName("тикер в нижнем регистре находит ту же бумагу")
    void shouldNormalizeLowercaseTicker() throws Exception {
        mockMvc.perform(post(TRANSACTIONS_URL)
                        .content(buildCreateRequest(userId, "SBER", "10", "250.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(PAGE_URL)
                        .content(buildTickerRequest(userId, "sber"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.security.ticker", is("SBER")));
    }

    @Test
    @DisplayName("бумаги нет в справочнике или у пользователя нет сделок по ней — 404")
    void shouldReturn404ForTickerWithoutTransactions() throws Exception {
        mockMvc.perform(post(PAGE_URL)
                        .content(buildTickerRequest(userId, "UNKN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("события SELL и BUY идут по убыванию даты сделки")
    void shouldOrderEventsByDateDescending() throws Exception {
        mockMvc.perform(post(TRANSACTIONS_URL)
                        .content(buildCreateRequest(userId, "SBER", "10", "250.00", "2026-01-15T10:00:00Z"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(post(TRANSACTIONS_URL)
                        .content(buildSellRequest(userId, "SBER", "4", "300.00", "2026-03-15T10:00:00Z"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(PAGE_URL)
                        .content(buildTickerRequest(userId, "SBER"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.events[0].kind", is("SELL")))
                .andExpect(jsonPath("$.body.events[0].realizedPnl", is(200.0)))
                .andExpect(jsonPath("$.body.events[1].kind", is("BUY")))
                .andExpect(jsonPath("$.body.events[1].first", is(true)));
    }

    private String buildCreateRequest(UUID reqUserId, String ticker, String quantity, String price) {
        return buildCreateRequest(reqUserId, ticker, quantity, price, "2026-01-15T10:00:00Z");
    }

    private String buildCreateRequest(UUID reqUserId, String ticker, String quantity, String price, String executedAt) {
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
                    "type": "BUY",
                    "securityType": "STOCK",
                    "quantity": %s,
                    "price": %s,
                    "executedAt": "%s"
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), ticker, quantity, price, executedAt);
    }

    private String buildSellRequest(UUID reqUserId, String ticker, String quantity, String price, String executedAt) {
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
                    "type": "SELL",
                    "securityType": "STOCK",
                    "quantity": %s,
                    "price": %s,
                    "executedAt": "%s"
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), ticker, quantity, price, executedAt);
    }

    private String buildTickerRequest(UUID reqUserId, String ticker) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "ticker": "%s"
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), ticker);
    }
}
