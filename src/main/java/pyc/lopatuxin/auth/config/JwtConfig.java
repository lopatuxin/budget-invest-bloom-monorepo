package pyc.lopatuxin.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация JWT токенов
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtConfig {

    /**
     * Секретный ключ для подписи JWT токенов
     */
    private String secret;

    /**
     * Время жизни access токена в миллисекундах
     */
    private Long accessTokenExpiration;

    /**
     * Время жизни refresh токена в миллисекундах
     */
    private Long refreshTokenExpiration;
}