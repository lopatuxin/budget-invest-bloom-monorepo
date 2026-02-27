package pyc.lopatuxin.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

/**
 * Конфигурация Spring Security для API Gateway (WebFlux).
 * <p>
 * Определяет правила доступа к эндпоинтам:
 * <ul>
 *   <li>Публичные пути доступны без JWT-токена.</li>
 *   <li>Все остальные пути требуют валидного JWT.</li>
 * </ul>
 * JWT-валидация делегируется {@code oauth2ResourceServer().jwt()},
 * который использует {@code JWT_ISSUER_URI}, заданный в {@code application-dev.yml}.
 * </p>
 * <p>
 * Аннотация {@code @EnableWebFluxSecurity} намеренно не используется:
 * бин {@link SecurityWebFilterChain} подхватывается Spring Security автоматически.
 * </p>
 * <p>
 * CORS-политика берётся из {@link CorsConfigurationSource}, зарегистрированного
 * в {@link CorsConfig} — отдельная регистрация в данном классе не требуется.
 * </p>
 */
@Configuration
public class SecurityConfig {

    /**
     * Источник CORS-конфигурации, зарегистрированный бином в {@link CorsConfig}.
     * <p>
     * Инжектируется для явной передачи в {@link ServerHttpSecurity#cors()},
     * чтобы Security-цепочка применяла те же CORS-правила, что и {@link org.springframework.web.cors.reactive.CorsWebFilter}.
     * </p>
     */
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * Конструктор с внедрением зависимости {@link CorsConfigurationSource}.
     *
     * @param corsConfigurationSource источник CORS-конфигурации из {@link CorsConfig}
     */
    public SecurityConfig(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /**
     * Настраивает {@link SecurityWebFilterChain} для реактивного стека WebFlux.
     * <p>
     * Публичные пути (JWT не требуется):
     * <ul>
     *   <li>{@code POST /api/auth/register}</li>
     *   <li>{@code POST /api/auth/login}</li>
     *   <li>{@code POST /api/refresh}</li>
     * </ul>
     * Защищённые пути (JWT обязателен):
     * <ul>
     *   <li>{@code POST /api/auth/logout}</li>
     *   <li>{@code /api/budget/**} — все методы</li>
     * </ul>
     * </p>
     * <p>
     * CSRF отключён — Gateway не использует сессии; защита обеспечивается JWT и HttpOnly cookie.
     * HTTP Basic отключён — не применим для REST/SPA-взаимодействия.
     * Form Login отключён — аутентификация происходит через Auth-сервис.
     * </p>
     *
     * @param http строитель {@link ServerHttpSecurity}
     * @return сконфигурированный {@link SecurityWebFilterChain}
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(corsSpec -> corsSpec.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/refresh").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .pathMatchers("/api/budget/**").authenticated()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(_ -> {
                        })
                )
                .build();
    }
}