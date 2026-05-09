package pyc.lopatuxin.investment.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class BudgetClientConfig {

    @Bean(destroyMethod = "close")
    public CloseableHttpClient budgetHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(2, TimeUnit.SECONDS))
                .setResponseTimeout(Timeout.of(5, TimeUnit.SECONDS))
                .build();
        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Bean
    public RestClient budgetRestClient(
            @Value("${budget.service.url}") String baseUrl,
            CloseableHttpClient budgetHttpClient) {
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(budgetHttpClient))
                .baseUrl(baseUrl)
                .build();
    }
}
