package pyc.lopatuxin.investment.client.moex;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the -parameters compiler flag / @PathVariable names combo:
 * builds MoexIssApi through a real HttpServiceProxyFactory (like the production
 * WebClientConfig bean does) on top of a MockWebServer, so a missing flag or a
 * missing explicit @PathVariable name fails the same way it does in production
 * ("Name for argument of type [java.lang.String] not specified") before any
 * request reaches the network. Response body parsing is irrelevant here — only
 * whether the ticker made it into the request path — so any exception raised
 * after the request was sent is ignored.
 */
@DisplayName("MoexIssApiParameterNamesTest — регрессия на -parameters и явные имена @PathVariable")
class MoexIssApiParameterNamesTest {

    private MockWebServer server;
    private MoexIssApi api;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        RestClient restClient = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        api = HttpServiceProxyFactory.builderFor(adapter).build().createClient(MoexIssApi.class);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("getSecurity — тикер подставляется в путь /securities/{ticker}.json")
    void getSecurity_substitutesTickerIntoPath() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{}")
                .addHeader("Content-Type", "application/json"));

        try {
            api.getSecurity("SBER", "securities", "no");
        } catch (Exception ignoredBodyConversionIssue) {
            // Only the outgoing request path matters for this regression test;
            // response body deserialization is exercised by MoexIssClientTest.
        }

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/securities/SBER.json?iss.only=securities&iss.meta=no");
    }
}
