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
import pyc.lopatuxin.investment.client.moex.dto.MoexSecurityDto;
import pyc.lopatuxin.investment.client.moex.dto.MoexSnapshotDto;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
}
