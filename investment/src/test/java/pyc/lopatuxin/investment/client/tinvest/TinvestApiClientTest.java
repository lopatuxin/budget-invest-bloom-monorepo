package pyc.lopatuxin.investment.client.tinvest;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pyc.lopatuxin.investment.config.TinvestClientConfig;
import pyc.lopatuxin.investment.config.TinvestProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Builds the exact bean chain TinvestClientConfig wires (CloseableHttpClient -> RestClient ->
 * HttpServiceProxyFactory) against a MockWebServer, the same way WebClientConfigTest does for
 * MOEX — this exercises the real request construction (path, headers, body) and the real
 * response-status handling, with TinvestResilience wrapping calls the same way DividendSyncService
 * and TinvestInstrumentResolver do in production.
 */
@DisplayName("TinvestApiClientTest — реальный HTTP-контракт T-Invest клиента")
class TinvestApiClientTest {

    private final TinvestClientConfig config = new TinvestClientConfig();
    private MockWebServer server;
    private CloseableHttpClient httpClient;
    private TinvestApi api;
    private TinvestResilience resilience;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        TinvestProperties props = new TinvestProperties();
        props.setBaseUrl(server.url("/").toString());
        props.setToken("test-token");
        props.setConnectTimeoutMs(2000);
        props.setTimeoutMs(2000);

        httpClient = config.tinvestHttpClient(props);
        RestClient restClient = config.tinvestRestClient(httpClient, props);
        api = config.tinvestApi(restClient);

        // RetryRegistry.retry(name) with a single argument always falls back to the registry's
        // DEFAULT config (see InMemoryRetryRegistry) — a config keyed by name in RetryRegistry.of(Map)
        // is only reachable via the two-argument retry(name, configName) form. Passing the config
        // directly here makes it the default, so retry("tinvest") actually uses it.
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(java.time.Duration.ofMillis(1))
                .ignoreExceptions(TinvestUnauthorizedException.class, TinvestRateLimitedException.class)
                .build());
        resilience = new TinvestResilience(retryRegistry, CircuitBreakerRegistry.ofDefaults());
        resilience.init();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
        httpClient.close();
    }

    @Test
    @DisplayName("findInstrument — POST на верный путь с заголовками Authorization/x-app-name и телом запроса")
    void findInstrument_sendsCorrectPathHeadersAndBody() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setBody("""
                        {"instruments":[{"ticker":"SBER","classCode":"TQBR","uid":"e6123145-9665-43e0-8413-cd61b8aa9b13"}]}
                        """)
                .addHeader("Content-Type", "application/json"));

        TinvestFindInstrumentResponse response = resilience.execute("findInstrument",
                () -> api.findInstrument(new TinvestFindInstrumentRequest("SBER", "INSTRUMENT_TYPE_SHARE")));

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath())
                .isEqualTo("/tinkoff.public.invest.api.contract.v1.InstrumentsService/FindInstrument");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-token");
        assertThat(recorded.getHeader("x-app-name")).isEqualTo("lopatuxin.budget-invest-bloom");
        assertThat(recorded.getBody().readUtf8()).contains("\"query\":\"SBER\"", "\"instrumentKind\":\"INSTRUMENT_TYPE_SHARE\"");

        assertThat(response.instruments()).hasSize(1);
        assertThat(response.instruments().get(0).classCode()).isEqualTo("TQBR");
        assertThat(response.instruments().get(0).uid()).isEqualTo("e6123145-9665-43e0-8413-cd61b8aa9b13");
    }

    @Test
    @DisplayName("getDividends — POST на верный путь с телом from/to в RFC3339")
    void getDividends_sendsCorrectPathAndBody() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("{\"dividends\":[]}").addHeader("Content-Type", "application/json"));

        resilience.execute("getDividends", () -> api.getDividends(new TinvestDividendsRequest(
                "uid-1", Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"))));

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getPath())
                .isEqualTo("/tinkoff.public.invest.api.contract.v1.InstrumentsService/GetDividends");
        assertThat(recorded.getBody().readUtf8())
                .contains("\"instrumentId\":\"uid-1\"", "\"from\":\"2024-01-01T00:00:00Z\"", "\"to\":\"2026-01-01T00:00:00Z\"");
    }

    @Test
    @DisplayName("getDividends — разбор реального эталонного ответа T-Invest для SBER")
    void getDividends_parsesRealSberFixture() {
        server.enqueue(new MockResponse().setBody(loadFixture("/tinvest/get-dividends-sber.json"))
                .addHeader("Content-Type", "application/json"));

        TinvestDividendsResponse response = resilience.execute("getDividends", () -> api.getDividends(
                new TinvestDividendsRequest("uid", Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"))));

        assertThat(response.dividends()).hasSize(6);
        TinvestDividend latest = response.dividends().get(0);
        assertThat(latest.recordDate()).isEqualTo(Instant.parse("2026-07-20T00:00:00Z"));
        assertThat(latest.paymentDate()).isEqualTo(Instant.parse("2026-08-04T00:00:00Z"));
        assertThat(latest.dividendNet().currency()).isEqualTo("rub");
        assertThat(latest.dividendNet().units()).isEqualTo("37");
        assertThat(latest.dividendNet().toAmount()).isEqualByComparingTo(new BigDecimal("37.64"));

        TinvestDividend zeroNano = response.dividends().stream()
                .filter(d -> "25".equals(d.dividendNet().units())).findFirst().orElseThrow();
        assertThat(zeroNano.dividendNet().toAmount()).isEqualByComparingTo(new BigDecimal("25"));
    }

    @Test
    @DisplayName("getDividends — paymentDate отсутствует в ответе → null, отрицательные units и nano — один знак")
    void getDividends_missingPaymentDate_andNegativeAmount() {
        server.enqueue(new MockResponse().setBody("""
                {"dividends":[{
                  "dividendNet": {"currency": "RUB", "units": "-5", "nano": -500000000},
                  "recordDate": "2026-03-01T00:00:00Z"
                }]}
                """).addHeader("Content-Type", "application/json"));

        TinvestDividendsResponse response = resilience.execute("getDividends", () -> api.getDividends(
                new TinvestDividendsRequest("uid", Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"))));

        TinvestDividend dto = response.dividends().get(0);
        assertThat(dto.paymentDate()).isNull();
        assertThat(dto.dividendNet().toAmount()).isEqualByComparingTo(new BigDecimal("-5.5"));
    }

    @Test
    @DisplayName("getBondCoupons — POST на верный путь с телом instrumentId/from/to в RFC3339")
    void getBondCoupons_sendsCorrectPathAndBody() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("{\"events\":[]}").addHeader("Content-Type", "application/json"));

        resilience.execute("getBondCoupons", () -> api.getBondCoupons(new TinvestBondCouponsRequest(
                "uid-ofz", Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"))));

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getPath())
                .isEqualTo("/tinkoff.public.invest.api.contract.v1.InstrumentsService/GetBondCoupons");
        assertThat(recorded.getBody().readUtf8())
                .contains("\"instrumentId\":\"uid-ofz\"", "\"from\":\"2024-01-01T00:00:00Z\"", "\"to\":\"2026-01-01T00:00:00Z\"");
    }

    @Test
    @DisplayName("getBondCoupons — разбор реального эталонного ответа T-Invest для ОФЗ")
    void getBondCoupons_parsesRealOfzFixture() {
        server.enqueue(new MockResponse().setBody(loadFixture("/tinvest/get-bond-coupons-ofz.json"))
                .addHeader("Content-Type", "application/json"));

        TinvestBondCouponsResponse response = resilience.execute("getBondCoupons", () -> api.getBondCoupons(
                new TinvestBondCouponsRequest("uid-ofz", Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"))));

        assertThat(response.events()).hasSize(2);
        TinvestCoupon latest = response.events().get(0);
        assertThat(latest.couponDate()).isEqualTo(Instant.parse("2026-08-19T00:00:00Z"));
        assertThat(latest.fixDate()).isEqualTo(Instant.parse("2026-08-18T00:00:00Z"));
        assertThat(latest.couponNumber()).isEqualTo(18L);
        assertThat(latest.couponType()).isEqualTo("COUPON_TYPE_CONSTANT");
        assertThat(latest.payOneBond().currency()).isEqualTo("rub");
        assertThat(latest.payOneBond().toAmount()).isEqualByComparingTo(new BigDecimal("38.64"));
    }

    @Test
    @DisplayName("getBondCoupons — fixDate отсутствует в ответе → null")
    void getBondCoupons_missingFixDate_isNull() {
        server.enqueue(new MockResponse().setBody("""
                {"events":[{
                  "payOneBond": {"currency": "RUB", "units": "38", "nano": 640000000},
                  "couponDate": "2026-08-19T00:00:00Z",
                  "couponNumber": "18"
                }]}
                """).addHeader("Content-Type", "application/json"));

        TinvestBondCouponsResponse response = resilience.execute("getBondCoupons", () -> api.getBondCoupons(
                new TinvestBondCouponsRequest("uid-ofz", Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"))));

        assertThat(response.events().get(0).fixDate()).isNull();
    }

    @Test
    @DisplayName("401 → TinvestUnauthorizedException, повтор не выполняется")
    void unauthorized_throwsWithoutRetry() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"code\":16,\"message\":\"UNAUTHENTICATED\"}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> resilience.execute("findInstrument",
                () -> api.findInstrument(new TinvestFindInstrumentRequest("SBER", "INSTRUMENT_TYPE_SHARE"))))
                .isInstanceOf(TinvestUnauthorizedException.class);

        assertThat(server.getRequestCount()).isEqualTo(1);
        server.takeRequest();

        // Second call short-circuits without touching the network at all — disabled until restart.
        assertThatThrownBy(() -> resilience.execute("findInstrument",
                () -> api.findInstrument(new TinvestFindInstrumentRequest("SBER", "INSTRUMENT_TYPE_SHARE"))))
                .isInstanceOf(TinvestUnauthorizedException.class);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("429 → повтор через паузу, взятую из x-ratelimit-reset")
    void rateLimited_retriesAfterHeaderDrivenPause() {
        server.enqueue(new MockResponse().setResponseCode(429).addHeader("x-ratelimit-reset", "0"));
        server.enqueue(new MockResponse().setBody("{\"dividends\":[]}").addHeader("Content-Type", "application/json"));

        TinvestDividendsResponse response = resilience.execute("getDividends", () -> api.getDividends(
                new TinvestDividendsRequest("uid", Instant.EPOCH, Instant.EPOCH)));

        assertThat(response.dividends()).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("5xx — ретрай настроенное число раз, затем запасной режим (TinvestUnavailableException)")
    void serverError_retriesThenFallsBack() {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> resilience.execute("getDividends", () -> api.getDividends(
                new TinvestDividendsRequest("uid", Instant.EPOCH, Instant.EPOCH))))
                .isInstanceOf(TinvestUnavailableException.class);

        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    private String loadFixture(String classpath) {
        try (InputStream is = getClass().getResourceAsStream(classpath)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + classpath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
