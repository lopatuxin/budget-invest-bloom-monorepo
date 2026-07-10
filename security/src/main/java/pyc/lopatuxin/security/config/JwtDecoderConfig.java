package pyc.lopatuxin.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Servlet-stack JWT decoder for the monolith security perimeter.
 * The access JWT is signed by the auth module with HS256 on a shared secret
 * (property jwt.secret) — the same secret, now in-process. No OIDC issuer.
 */
@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String jwtSecret) {
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();

        // Defense-in-depth: access tokens issued by auth carry no "type" claim, so they pass;
        // refresh tokens carry type=refresh and must never be accepted as an access token here.
        OAuth2TokenValidator<Jwt> notRefresh = jwt ->
                "refresh".equals(jwt.getClaimAsString("type"))
                        ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                                "Refresh token cannot be used as access token", null))
                        : OAuth2TokenValidatorResult.success();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), notRefresh));

        return decoder;
    }
}
