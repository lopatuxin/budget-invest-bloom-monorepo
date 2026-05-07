package pyc.lopatuxin.investment.client.moex;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import pyc.lopatuxin.investment.dto.response.MoexCandleDto;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoexIssClientParseHistoryPageTest — guard-ветки parseHistoryPage")
class MoexIssClientParseHistoryPageTest {

    private MockWebServer mockWebServer;
    private MoexIssClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        client = new MoexIssClient(restClient, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // -----------------------------------------------------------------------
    // close == null
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("parseHistoryPage — строка с close=null пропускается, остальные возвращаются")
    void fetchHistory_rowWithNullClose_isSkipped() {
        String response = historyWithRows(
                // close=null row
                "[\"TQBR\",\"2024-01-10\",\"SBER\",\"SBER\",100,270000.0,270.0,268.0,272.0,null,270.5,null,1000,null,null,null,null,null,null,null,1]",
                // valid row
                "[\"TQBR\",\"2024-01-15\",\"SBER\",\"SBER\",200,540000.0,270.0,268.0,272.0,271.0,270.5,271.0,2000,null,null,null,null,null,null,null,1]"
        );
        enqueue(response);

        List<MoexCandleDto> result = client.fetchHistory("SBER", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tradeDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(result.get(0).close()).isEqualByComparingTo(new BigDecimal("271.0"));
    }

    // -----------------------------------------------------------------------
    // close == 0
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("parseHistoryPage — строка с close=0 пропускается, остальные возвращаются")
    void fetchHistory_rowWithZeroClose_isSkipped() {
        String response = historyWithRows(
                // close=0 row
                "[\"TQBR\",\"2024-01-10\",\"SBER\",\"SBER\",0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,null,null,null,null,null,null,null,1]",
                // valid row
                "[\"TQBR\",\"2024-01-15\",\"SBER\",\"SBER\",200,540000.0,270.0,268.0,272.0,271.0,270.5,271.0,2000,null,null,null,null,null,null,null,1]"
        );
        enqueue(response);

        List<MoexCandleDto> result = client.fetchHistory("SBER", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).close()).isEqualByComparingTo(new BigDecimal("271.0"));
    }

    // -----------------------------------------------------------------------
    // dateStr == null
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("parseHistoryPage — строка с dateStr=null пропускается, остальные возвращаются")
    void fetchHistory_rowWithNullDate_isSkipped() {
        String response = historyWithRows(
                // TRADEDATE=null row (but valid close)
                "[\"TQBR\",null,\"SBER\",\"SBER\",100,270000.0,270.0,268.0,272.0,271.0,270.5,271.0,1000,null,null,null,null,null,null,null,1]",
                // valid row
                "[\"TQBR\",\"2024-01-15\",\"SBER\",\"SBER\",200,540000.0,270.0,268.0,272.0,271.0,270.5,271.0,2000,null,null,null,null,null,null,null,1]"
        );
        enqueue(response);

        List<MoexCandleDto> result = client.fetchHistory("SBER", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tradeDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    // -----------------------------------------------------------------------
    // All three bad rows together — none survive
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("parseHistoryPage — все строки с невалидными close/date пропускаются: результат пустой")
    void fetchHistory_allRowsInvalid_returnsEmpty() {
        // All rows fail the guard → shares page returns empty list →
        // fetchHistoryFromMarket falls through to bonds market (second request).
        // Enqueue bonds response as empty so MockWebServer does not time out.
        String allBadRows = historyWithRows(
                "[\"TQBR\",\"2024-01-10\",\"SBER\",\"SBER\",0,0.0,0.0,0.0,0.0,0.0,0.0,null,0,null,null,null,null,null,null,null,1]",
                "[\"TQBR\",\"2024-01-11\",\"SBER\",\"SBER\",0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,null,null,null,null,null,null,null,1]",
                "[\"TQBR\",null,\"SBER\",\"SBER\",0,0.0,0.0,0.0,0.0,0.0,0.0,271.0,0,null,null,null,null,null,null,null,1]"
        );
        enqueue(allBadRows);           // shares — all rows filtered out
        enqueue(emptyHistoryResponse()); // bonds — also empty

        List<MoexCandleDto> result = client.fetchHistory("SBER", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a MOEX history JSON response with the given raw row strings.
     * Columns match the real MOEX layout used in MoexIssClient.
     */
    private String historyWithRows(String... rows) {
        String dataJoined = String.join(",\n      ", rows);
        return """
                {
                  "history": {
                    "columns": ["BOARDID","TRADEDATE","SHORTNAME","SECID","NUMTRADES","VALUE","OPEN","LOW","HIGH","LEGALCLOSEPRICE","WAPRICE","CLOSE","VOLUME","MARKETPRICE2","MARKETPRICE3","ADMITTEDQUOTE","MP2VALTRD","MARKETPRICE3TRADESVALUE","ADMITTEDVALUE","WAVAL","TRADINGSESSION"],
                    "data": [
                      %s
                    ]
                  },
                  "history.cursor": {
                    "columns": ["INDEX","TOTAL","PAGESIZE"],
                    "data": [[0,1,100]]
                  }
                }
                """.formatted(dataJoined);
    }

    private String emptyHistoryResponse() {
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

    private void enqueue(String body) {
        mockWebServer.enqueue(new MockResponse()
                .setBody(body)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
    }
}
