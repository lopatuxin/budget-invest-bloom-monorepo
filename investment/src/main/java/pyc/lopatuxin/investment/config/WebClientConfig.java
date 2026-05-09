package pyc.lopatuxin.investment.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import pyc.lopatuxin.investment.client.moex.MoexIssApi;
import pyc.lopatuxin.investment.client.moex.MoexNotFoundException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean(destroyMethod = "close")
    public CloseableHttpClient moexHttpClient(MoexProperties props) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(props.getConnectTimeoutMs(), TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(props.getTimeoutMs(), TimeUnit.MILLISECONDS))
                .build();
        var pool = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(props.getMaxConnections())
                .setMaxConnPerRoute(props.getMaxConnectionsPerRoute())
                .build();
        return HttpClients.custom()
                .setConnectionManager(pool)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Bean
    public RestClient moexRestClient(CloseableHttpClient moexHttpClient, MoexProperties props) {
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(moexHttpClient))
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Accept", "application/json")
                .messageConverters(converters -> {
                    var jackson = new MappingJackson2HttpMessageConverter();
                    jackson.setSupportedMediaTypes(List.of(
                            MediaType.APPLICATION_JSON,
                            MediaType.TEXT_HTML,
                            new MediaType("application", "*+json")
                    ));
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(jackson);
                })
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status == HttpStatus.NOT_FOUND) {
                        throw new MoexNotFoundException("MOEX 404: " + req.getURI());
                    }
                    byte[] body;
                    try {
                        body = res.getBody().readAllBytes();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read error response body", e);
                    }
                    HttpHeaders headers = res.getHeaders();
                    Charset charset = Optional.ofNullable(headers.getContentType())
                            .map(MediaType::getCharset)
                            .orElse(StandardCharsets.UTF_8);
                    throw HttpClientErrorException.create(status, res.getStatusText(), headers, body, charset);
                })
                .build();
    }

    @Bean
    public MoexIssApi moexIssApi(RestClient moexRestClient) {
        RestClientAdapter adapter = RestClientAdapter.create(moexRestClient);
        return HttpServiceProxyFactory.builderFor(adapter).build().createClient(MoexIssApi.class);
    }
}
