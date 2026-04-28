package pyc.lopatuxin.investment.client.moex;

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
import pyc.lopatuxin.investment.client.moex.dto.MoexDividendDto;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoexIssClientFetchDividendsTest — юнит-тесты с MockWebServer")
class MoexIssClientFetchDividendsTest {

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
    @DisplayName("fetchDividends — MOEX вернул два дивиденда → список содержит 2 элемента с корректными полями")
    void fetchDividends_returnsListFromMoex() {
        String responseBody = """
                {
                  "dividends": {
                    "columns": ["secid","registryclosedate","dividendpaymentdate","value","currencyid"],
                    "data": [
                      ["SBER","2023-05-15","2023-07-21",25.0,"RUB"],
                      ["SBER","2022-05-20","2022-07-15",18.7,"RUB"]
                    ]
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        List<MoexDividendDto> result = client.fetchDividends("SBER");

        assertThat(result).hasSize(2);

        MoexDividendDto first = result.get(0);
        assertThat(first.getSecid()).isEqualTo("SBER");
        assertThat(first.getRegistryCloseDate()).isEqualTo(LocalDate.of(2023, 5, 15));
        assertThat(first.getDividendPaymentDate()).isEqualTo(LocalDate.of(2023, 7, 21));
        assertThat(first.getValue()).isEqualByComparingTo(new BigDecimal("25.0"));
        assertThat(first.getCurrencyId()).isEqualTo("RUB");

        MoexDividendDto second = result.get(1);
        assertThat(second.getRegistryCloseDate()).isEqualTo(LocalDate.of(2022, 5, 20));
        assertThat(second.getValue()).isEqualByComparingTo(new BigDecimal("18.7"));
    }

    @Test
    @DisplayName("fetchDividends — MOEX вернул пустой data: [] → результат пустой список")
    void fetchDividends_returnsEmpty_whenDataArrayEmpty() {
        String responseBody = """
                {
                  "dividends": {
                    "columns": ["secid","registryclosedate","dividendpaymentdate","value","currencyid"],
                    "data": []
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        List<MoexDividendDto> result = client.fetchDividends("SBER");

        assertThat(result).isEmpty();
    }
}
