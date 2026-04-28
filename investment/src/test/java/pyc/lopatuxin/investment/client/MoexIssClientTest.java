package pyc.lopatuxin.investment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import pyc.lopatuxin.investment.client.moex.MoexIssClient;
import pyc.lopatuxin.investment.client.moex.MoexUnavailableException;
import pyc.lopatuxin.investment.client.moex.dto.MoexCandleDto;
import pyc.lopatuxin.investment.client.moex.dto.MoexSecurityDto;
import pyc.lopatuxin.investment.client.moex.dto.MoexSnapshotDto;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MoexIssClientTest — юнит-тесты с MockWebServer")
class MoexIssClientTest {

    private MockWebServer mockWebServer;
    private MoexIssClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        client = new MoexIssClient(webClient, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("fetchSecurity — возвращает корректный MoexSecurityDto для SBER")
    void fetchSecurity_returnsDtoWhenDataPresent() {
        String responseBody = """
                {
                  "description": {
                    "columns": ["name","title","value"],
                    "data": [
                      ["SECID","Код","SBER"],
                      ["NAME","Наименование","Сбербанк"],
                      ["TYPENAME","Тип","Акция обыкновенная"],
                      ["CURRENCYID","Валюта","RUB"]
                    ]
                  },
                  "securities": {
                    "columns": ["SECID","BOARDID","SHORTNAME"],
                    "data": [["SBER","TQBR","Сбербанк"]]
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        Optional<MoexSecurityDto> result = client.fetchSecurity("SBER");

        assertThat(result).isPresent();
        MoexSecurityDto dto = result.get();
        assertThat(dto.ticker()).isEqualTo("SBER");
        assertThat(dto.name()).isEqualTo("Сбербанк");
        assertThat(dto.boardId()).isEqualTo("TQBR");
        assertThat(dto.securityType()).isEqualTo(SecurityType.STOCK);
        assertThat(dto.currency()).isEqualTo("RUB");
    }

    @Test
    @DisplayName("fetchSnapshots — возвращает Map с lastPrice для SBER")
    void fetchSnapshots_returnsMapWithLastPrice() {
        String sharesResponse = """
                {
                  "marketdata": {
                    "columns": ["SECID","LAST","PREVPRICE"],
                    "data": [["SBER","310.50","308.00"]]
                  }
                }
                """;
        // shares board response
        mockWebServer.enqueue(new MockResponse()
                .setBody(sharesResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        // bonds board response (empty)
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"marketdata\":{\"columns\":[\"SECID\",\"LAST\",\"PREVPRICE\"],\"data\":[]}}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        Map<String, MoexSnapshotDto> result = client.fetchSnapshots(List.of("SBER"));

        assertThat(result).containsKey("SBER");
        MoexSnapshotDto snap = result.get("SBER");
        assertThat(snap.lastPrice()).isEqualByComparingTo(new BigDecimal("310.50"));
        assertThat(snap.previousClose()).isEqualByComparingTo(new BigDecimal("308.00"));
    }

    @Test
    @DisplayName("fetchSecurity — пустой data блок возвращает Optional.empty()")
    void fetchSecurity_returnsEmptyWhenDataEmpty() {
        String responseBody = """
                {
                  "description": {"columns": ["name","title","value"], "data": []},
                  "securities": {"columns": ["SECID","BOARDID","SHORTNAME"], "data": []}
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        Optional<MoexSecurityDto> result = client.fetchSecurity("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchHistory — парсит первую страницу, возвращает корректные MoexCandleDto")
    void fetchHistory_parsesFirstPage() {
        String response = buildHistoryResponse("SBER", 0, 1, 100);
        mockWebServer.enqueue(new MockResponse()
                .setBody(response)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        List<MoexCandleDto> result = client.fetchHistory("SBER", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).hasSize(1);
        MoexCandleDto candle = result.get(0);
        assertThat(candle.ticker()).isEqualTo("SBER");
        assertThat(candle.tradeDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(candle.close()).isEqualByComparingTo(new BigDecimal("271.0"));
        assertThat(candle.open()).isEqualByComparingTo(new BigDecimal("270.0"));
        assertThat(candle.volume()).isEqualTo(11000L);
    }

    @Test
    @DisplayName("fetchHistory — пагинация: два запроса при TOTAL=200, PAGESIZE=100")
    void fetchHistory_paginates() {
        String page1 = buildHistoryResponse("SBER", 0, 200, 100);
        String page2 = buildHistoryResponse("SBER", 100, 200, 100);
        mockWebServer.enqueue(new MockResponse().setBody(page1)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        mockWebServer.enqueue(new MockResponse().setBody(page2)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        List<MoexCandleDto> result = client.fetchHistory("SBER", LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1));

        assertThat(result).hasSize(2);
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("fetchHistory — shares пустой, bonds возвращает данные")
    void fetchHistory_fallsBackToBonds() {
        String emptyShares = buildEmptyHistoryResponse();
        String bondsResponse = buildHistoryResponse("LKOH", 0, 1, 100);
        mockWebServer.enqueue(new MockResponse().setBody(emptyShares)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        mockWebServer.enqueue(new MockResponse().setBody(bondsResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        List<MoexCandleDto> result = client.fetchHistory("LKOH", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ticker()).isEqualTo("LKOH");
    }

    @Test
    @DisplayName("fetchHistory — сервер 500 → RuntimeException (в проде Resilience4j вызовет MoexUnavailableException через fallback)")
    void fetchHistory_throwsOnUnavailable() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client.fetchHistory("SBER", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .isInstanceOf(RuntimeException.class);
    }

    private String buildHistoryResponse(String ticker, int index, int total, int pageSize) {
        return """
                {
                  "history": {
                    "columns": ["BOARDID","TRADEDATE","SHORTNAME","SECID","NUMTRADES","VALUE","OPEN","LOW","HIGH","LEGALCLOSEPRICE","WAPRICE","CLOSE","VOLUME","MARKETPRICE2","MARKETPRICE3","ADMITTEDQUOTE","MP2VALTRD","MARKETPRICE3TRADESVALUE","ADMITTEDVALUE","WAVAL","TRADINGSESSION"],
                    "data": [
                      ["TQBR","2024-01-15","%s","%s",12000,3000000.0,270.0,268.0,272.0,271.0,270.5,271.0,11000,null,null,null,null,null,null,null,1]
                    ]
                  },
                  "history.cursor": {
                    "columns": ["INDEX","TOTAL","PAGESIZE"],
                    "data": [[%d,%d,%d]]
                  }
                }
                """.formatted(ticker, ticker, index, total, pageSize);
    }

    private String buildEmptyHistoryResponse() {
        return """
                {
                  "history": {
                    "columns": ["BOARDID","TRADEDATE","SHORTNAME","SECID","NUMTRADES","VALUE","OPEN","LOW","HIGH","LEGALCLOSEPRICE","WAPRICE","CLOSE","VOLUME","MARKETPRICE2","MARKETPRICE3","ADMITTEDQUOTE","MP2VALTRD","MARKETPRICE3TRADESVALUE","ADMITTEDVALUE","WAVAL","TRADINGSESSION"],
                    "data": []
                  },
                  "history.cursor": {
                    "columns": ["INDEX","TOTAL","PAGESIZE"],
                    "data": [[0,0,100]]
                  }
                }
                """;
    }
}
