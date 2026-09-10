package pyc.lopatuxin.investment.config;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pyc.lopatuxin.investment.client.moex.MoexIssApi;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the RestClient message converter wiring in WebClientConfig:
 * Spring's default JSON converter on this classpath is the Jackson 3-based
 * JacksonJsonHttpMessageConverter, added ahead of the classic Jackson 2 converter
 * MoexIssApi needs for its com.fasterxml.jackson.databind.JsonNode return types.
 * Builds the exact bean chain WebClientConfig wires (CloseableHttpClient -> RestClient
 * -> HttpServiceProxyFactory) against a MockWebServer and asserts a real response body
 * parses into JsonNode instead of failing with a Jackson "Type definition error".
 */
@DisplayName("WebClientConfigTest — RestClient должен уметь десериализовать JsonNode")
class WebClientConfigTest {

    private final WebClientConfig config = new WebClientConfig();
    private MockWebServer server;
    private CloseableHttpClient httpClient;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Test
    @DisplayName("getSecurity — тело ответа парсится в JsonNode, а не падает с Type definition error")
    void getSecurity_deserializesResponseBodyIntoJsonNode() {
        server.enqueue(new MockResponse()
                .setBody("""
                        {
                          "securities": {
                            "columns": ["SECID","SHORTNAME"],
                            "data": [["SBER","Сбербанк"]]
                          }
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        MoexProperties props = new MoexProperties();
        props.setBaseUrl(server.url("/").toString());

        httpClient = config.moexHttpClient(props);
        RestClient restClient = config.moexRestClient(httpClient, props);
        MoexIssApi api = config.moexIssApi(restClient);

        JsonNode response = api.getSecurity("SBER", "securities", "no");

        assertThat(response.path("securities").path("data").get(0).get(0).asText()).isEqualTo("SBER");
    }
}
