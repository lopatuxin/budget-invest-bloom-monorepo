package pyc.lopatuxin.investment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pyc.lopatuxin.investment.AbstractIntegrationTest;
import pyc.lopatuxin.investment.dto.response.PortfolioValuePointDto;
import pyc.lopatuxin.investment.dto.response.PricePointDto;
import pyc.lopatuxin.investment.service.AnalyticsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Интеграционные тесты AnalyticsController")
class AnalyticsControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/investment/analytics";

    @MockitoBean
    private AnalyticsService analyticsService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("POST /portfolio/value-history — возвращает 200 и непустое тело")
    void portfolioValueHistory_returnsOk() throws Exception {
        List<PortfolioValuePointDto> mockResult = List.of(
                PortfolioValuePointDto.builder().date(LocalDate.of(2024, 1, 15)).value(new BigDecimal("2700.00")).build(),
                PortfolioValuePointDto.builder().date(LocalDate.of(2024, 1, 16)).value(new BigDecimal("2750.00")).build()
        );
        when(analyticsService.portfolioValueHistory(any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(mockResult);

        mockMvc.perform(post(BASE_URL + "/portfolio/value-history")
                        .content(buildPortfolioRequest(userId, "2024-01-01", "2024-01-31"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.body", notNullValue()))
                .andExpect(jsonPath("$.body", hasSize(2)));
    }

    @Test
    @DisplayName("POST /security/price-history — возвращает 200 и непустое тело")
    void securityPriceHistory_returnsOk() throws Exception {
        List<PricePointDto> mockResult = List.of(
                PricePointDto.builder().date(LocalDate.of(2024, 1, 15)).close(new BigDecimal("271.00")).build()
        );
        when(analyticsService.securityPriceHistory(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(mockResult);

        mockMvc.perform(post(BASE_URL + "/security/price-history")
                        .content(buildSecurityHistoryRequest(userId, "SBER", "2024-01-01", "2024-01-31"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.body", notNullValue()))
                .andExpect(jsonPath("$.body", hasSize(1)));
    }

    @Test
    @DisplayName("POST /portfolio/value-history — from после to возвращает 400")
    void portfolioValueHistory_invalidRange_returnsBadRequest() throws Exception {
        mockMvc.perform(post(BASE_URL + "/portfolio/value-history")
                        .content(buildPortfolioRequest(userId, "2024-12-31", "2024-01-01"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    private String buildPortfolioRequest(UUID reqUserId, String from, String to) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "from": "%s",
                    "to": "%s"
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), from, to);
    }

    private String buildSecurityHistoryRequest(UUID reqUserId, String ticker, String from, String to) {
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
                    "from": "%s",
                    "to": "%s"
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), ticker, from, to);
    }
}
