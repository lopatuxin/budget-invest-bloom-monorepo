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

@DisplayName("Интеграционные тесты PortfolioController")
class PortfolioControllerTest extends AbstractIntegrationTest {

    private static final String PORTFOLIO_URL = "/api/investment/portfolio";
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
    @DisplayName("2 BUY SBER → POST /page → 1 позиция с правильным qty")
    void shouldReturnOnePositionAfterTwoBuys() throws Exception {
        mockMvc.perform(post(TRANSACTIONS_URL)
                        .content(buildCreateRequest(userId, "SBER", "10", "250.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(TRANSACTIONS_URL)
                        .content(buildCreateRequest(userId, "SBER", "5", "280.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(PORTFOLIO_URL + "/page")
                        .content(buildPositionsRequest(userId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.body.positions", hasSize(1)))
                .andExpect(jsonPath("$.body.positions[0].ticker", is("SBER")))
                .andExpect(jsonPath("$.body.positions[0].quantity", is(15.0)));
    }

    @Test
    @DisplayName("POST /positions/by-ticker — вернуть позицию по тикеру SBER")
    void shouldReturnPositionByTicker() throws Exception {
        mockMvc.perform(post(TRANSACTIONS_URL)
                        .content(buildCreateRequest(userId, "SBER", "10", "250.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(PORTFOLIO_URL + "/positions/by-ticker")
                        .content(buildByTickerRequest(userId, "SBER"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.body.ticker", is("SBER")));
    }

    @Test
    @DisplayName("POST /positions/by-ticker с несуществующим тикером — 404")
    void shouldReturn404ForNonExistentTicker() throws Exception {
        mockMvc.perform(post(PORTFOLIO_URL + "/positions/by-ticker")
                        .content(buildByTickerRequest(userId, "UNKN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    private String buildCreateRequest(UUID reqUserId, String ticker, String quantity, String price) {
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
                    "executedAt": "2026-01-15T10:00:00Z"
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), ticker, quantity, price);
    }

    private String buildPositionsRequest(UUID reqUserId) {
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

    private String buildByTickerRequest(UUID reqUserId, String ticker) {
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
