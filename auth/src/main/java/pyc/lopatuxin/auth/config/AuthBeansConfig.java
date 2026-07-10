package pyc.lopatuxin.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Инфраструктурные бины модуля auth.
 * Единая цепочка безопасности реализована в модуле security (периметр монолита),
 * здесь остаются только бины, нужные доменной логике auth.
 */
@Configuration
public class AuthBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Фильтр для обработки заголовков прокси (X-Forwarded-For, X-Real-IP и т.д.)
     * После настройки этого фильтра request.getRemoteAddr() будет возвращать реальный IP клиента
     */
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }

    /**
     * Резолвер для извлечения Bearer токена из заголовка Authorization.
     * Это стандартная реализация Spring Security, которая извлекает токен из заголовка
     * вида "Authorization: Bearer <token>"
     */
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        return new DefaultBearerTokenResolver();
    }
}
