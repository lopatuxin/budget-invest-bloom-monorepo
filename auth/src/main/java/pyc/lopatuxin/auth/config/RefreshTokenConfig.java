package pyc.lopatuxin.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * Configuration for refresh token hashing
 */
@Configuration
@ConfigurationProperties(prefix = "auth.refresh-token")
@Getter
@Setter
public class RefreshTokenConfig {

    /**
     * Server-side secret for HMAC-SHA256 hashing of refresh tokens.
     * Protects against offline hash cracking if the DB is leaked.
     */
    private String pepper;

    @PostConstruct
    public void validate() {
        Assert.hasText(pepper, "auth.refresh-token.pepper must be configured");
    }
}
