package pyc.lopatuxin.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Конфигурация CORS для API Gateway.
 * <p>
 * Регистрирует {@link CorsWebFilter}, разрешающий фронтенду обращаться к Gateway
 * с нужными HTTP-методами и заголовками. Список разрешённых origins читается из
 * переменной окружения {@code GATEWAY_CORS_ALLOWED_ORIGINS}; при её отсутствии
 * используется значение по умолчанию {@code http://localhost:8080}.
 * </p>
 * <p>
 * Настройка {@code allowCredentials = true} обязательна для корректной передачи
 * HttpOnly cookie с refresh-токеном из браузера через Gateway в Auth-сервис.
 * </p>
 */
@Configuration
public class CorsConfig {

    /**
     * Список разрешённых origins, читается из конфигурационного свойства
     * {@code gateway.cors.allowed-origins}.
     * <p>
     * Значение подставляется через переменную окружения
     * {@code GATEWAY_CORS_ALLOWED_ORIGINS} (по умолчанию: {@code http://localhost:8080}).
     * </p>
     */
    @Value("${gateway.cors.allowed-origins:http://localhost:8080}")
    private String allowedOrigins;

    /**
     * Создаёт и регистрирует {@link CorsWebFilter} для всех путей ({@code /**}).
     * <p>
     * Разрешённые HTTP-методы: {@code GET}, {@code POST}, {@code PUT},
     * {@code DELETE}, {@code OPTIONS}.
     * </p>
     * <p>
     * Разрешённые заголовки: {@code Authorization}, {@code Content-Type},
     * {@code X-Request-ID}.
     * </p>
     *
     * @return настроенный {@link CorsWebFilter}
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();

        corsConfiguration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID"));
        corsConfiguration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);

        return new CorsWebFilter(source);
    }
}