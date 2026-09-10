package pyc.lopatuxin.investment.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import pyc.lopatuxin.investment.client.tinvest.TinvestApi;
import pyc.lopatuxin.investment.client.tinvest.TinvestRateLimitedException;
import pyc.lopatuxin.investment.client.tinvest.TinvestUnauthorizedException;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.concurrent.TimeUnit;

@Configuration
public class TinvestClientConfig {

    private static final long DEFAULT_RATE_LIMIT_WAIT_SECONDS = 60;

    // T-Invest (invest-public-api.tbank.ru) serves a TLS chain rooted at the Russian national CA,
    // which the JRE's default cacerts does not trust. Trust for that CA is scoped to this one
    // HttpClient only, via its own truststore/SSLContext, instead of importing the CA into the
    // JVM-wide cacerts (see app/Dockerfile history) — so it does not leak into every other
    // outgoing TLS connection the monolith makes (MOEX, etc.).
    // The certs themselves are public CA root/sub certificates (Russian Ministry of Digital
    // Development), not secrets, so they are vendored as plain PEM resources and the truststore
    // is assembled in memory at startup instead of shipping a prebuilt binary keystore.
    private static final String[] TRUSTED_CERT_RESOURCES = {
            "/tinvest-certs/russian_trusted_root_ca.crt",
            "/tinvest-certs/russian_trusted_sub_ca.crt"
    };

    @Bean(destroyMethod = "close")
    public CloseableHttpClient tinvestHttpClient(TinvestProperties props) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(props.getConnectTimeoutMs(), TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(props.getTimeoutMs(), TimeUnit.MILLISECONDS))
                .build();
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(tinvestSslSocketFactory())
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                // Apache HttpClient5 retries certain statuses/IOExceptions on its own by default
                // (e.g. a 429 with a Retry-After header) — that would race with, and duplicate,
                // the retry TinvestResilience already does (resilience4j is the single source of
                // truth here, since it is the layer that knows about circuit-breaking and the
                // "disabled after 401" rule).
                .disableAutomaticRetries()
                .build();
    }

    // Builds an in-memory truststore from the vendored root+sub CA PEM certs, used only by
    // tinvestHttpClient above — deliberately not merged with the JVM default trust anchors,
    // since this HttpClient talks to T-Invest only.
    private SSLConnectionSocketFactory tinvestSslSocketFactory() {
        try {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(null, null);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            for (String resource : TRUSTED_CERT_RESOURCES) {
                try (InputStream in = getClass().getResourceAsStream(resource)) {
                    Certificate certificate = certificateFactory.generateCertificate(in);
                    trustStore.setCertificateEntry(resource, certificate);
                }
            }
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(trustStore, null)
                    .build();
            return SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sslContext)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось загрузить хранилище доверенных сертификатов T-Invest", e);
        }
    }

    @Bean
    public RestClient tinvestRestClient(CloseableHttpClient tinvestHttpClient, TinvestProperties props) {
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(tinvestHttpClient))
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getToken())
                .defaultHeader("x-app-name", "lopatuxin.budget-invest-bloom")
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status == HttpStatus.UNAUTHORIZED) {
                        throw new TinvestUnauthorizedException("T-Invest 401: " + req.getURI());
                    }
                    if (status.value() == 429) {
                        throw new TinvestRateLimitedException(parseRetryAfterSeconds(res.getHeaders()));
                    }
                    throw HttpClientErrorException.create(
                            status, res.getStatusText(), res.getHeaders(), new byte[0], StandardCharsets.UTF_8);
                })
                .build();
    }

    @Bean
    public TinvestApi tinvestApi(RestClient tinvestRestClient) {
        RestClientAdapter adapter = RestClientAdapter.create(tinvestRestClient);
        return HttpServiceProxyFactory.builderFor(adapter).build().createClient(TinvestApi.class);
    }

    // The T-Invest API returns the pause in a response header on 429 (superseding a fixed
    // resilience4j backoff, see TinvestResilience); a missing or malformed header falls back
    // to a conservative 60s so a single bad response never turns into a tight retry loop.
    static long parseRetryAfterSeconds(HttpHeaders headers) {
        String header = headers.getFirst("x-ratelimit-reset");
        if (header == null) {
            return DEFAULT_RATE_LIMIT_WAIT_SECONDS;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_RATE_LIMIT_WAIT_SECONDS;
        }
    }
}
