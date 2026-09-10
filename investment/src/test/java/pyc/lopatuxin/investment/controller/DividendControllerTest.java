package pyc.lopatuxin.investment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pyc.lopatuxin.investment.AbstractIntegrationTest;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.DividendSource;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Интеграционные тесты DividendController")
class DividendControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/investment/dividends";

    private UUID userId;

    @BeforeEach
    void setUp() {
        dividendRepository.deleteAll();
        positionRepository.deleteAll();
        securityRepository.deleteAll();
        userId = UUID.randomUUID();

        Security sber = securityRepository.save(Security.builder()
                .ticker("SBER").name("Сбербанк").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY).build());
        positionRepository.save(Position.builder()
                .userId(userId).security(sber)
                .quantity(new BigDecimal("100")).averagePrice(new BigDecimal("280.00"))
                .totalCost(new BigDecimal("28000.00")).build());
    }

    @Test
    @DisplayName("POST /dividends — создаёт дивиденд вручную, возвращает 201 с source=MANUAL")
    void create_returnsCreatedWithManualSource() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(buildCreateRequest(userId, "SBER", "2026-07-18", "34.84"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is(201)))
                .andExpect(jsonPath("$.body.source", is("MANUAL")))
                .andExpect(jsonPath("$.body.amountPerShare", is(34.84)));
    }

    @Test
    @DisplayName("POST /dividends — дубль даты отсечки → 409 с кодом DIVIDEND_EXISTS")
    void create_duplicateRecordDate_returnsConflict() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(buildCreateRequest(userId, "SBER", "2026-07-18", "34.84"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(BASE_URL)
                        .content(buildCreateRequest(userId, "SBER", "2026-07-18", "10.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.body.code", is("DIVIDEND_EXISTS")));
    }

    @Test
    @DisplayName("POST /dividends — бумага не в портфеле пользователя → 404")
    void create_tickerNotInPortfolio_returnsNotFound() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(buildCreateRequest(userId, "GAZP", "2026-07-18", "10.00"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /dividends/delete — удаляет ручной дивиденд, возвращает 200")
    void delete_removesManualDividend_returnsOk() throws Exception {
        Dividend manual = dividendRepository.save(Dividend.builder()
                .security(securityRepository.findById("SBER").orElseThrow())
                .recordDate(LocalDate.now()).amountPerShare(new BigDecimal("10.00"))
                .currency("RUB").status(DividendStatus.ANNOUNCED).source(DividendSource.MANUAL).build());

        mockMvc.perform(post(BASE_URL + "/delete")
                        .content(buildDeleteRequest(userId, manual.getId()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)));
    }

    @Test
    @DisplayName("POST /dividends/delete — синхронизированный дивиденд удалить нельзя → 400")
    void delete_nonManualDividend_returnsBadRequest() throws Exception {
        Dividend synced = dividendRepository.save(Dividend.builder()
                .security(securityRepository.findById("SBER").orElseThrow())
                .recordDate(LocalDate.now()).amountPerShare(new BigDecimal("10.00"))
                .currency("RUB").status(DividendStatus.ANNOUNCED).source(DividendSource.TINVEST).build());

        mockMvc.perform(post(BASE_URL + "/delete")
                        .content(buildDeleteRequest(userId, synced.getId()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    private String buildCreateRequest(UUID reqUserId, String ticker, String recordDate, String amount) {
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
                    "recordDate": "%s",
                    "amountPerShare": %s
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), ticker, recordDate, amount);
    }

    private String buildDeleteRequest(UUID reqUserId, UUID dividendId) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "dividendId": "%s"
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), dividendId);
    }
}
