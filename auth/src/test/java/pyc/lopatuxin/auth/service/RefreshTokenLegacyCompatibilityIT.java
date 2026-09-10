package pyc.lopatuxin.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pyc.lopatuxin.auth.AbstractIntegrationTest;
import pyc.lopatuxin.auth.config.JwtConfig;
import pyc.lopatuxin.auth.entity.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Deliberately not @Transactional on the test method, same reasoning as RefreshTokenReuseIT: a
// transaction wrapped around the whole request keeps the persistence context open for the entire
// call, so lazy associations (e.g. User.roles) stay reachable even when the production code path
// forgot to load them eagerly inside its own, real transaction boundary. Only a real, uncontrolled
// request reproduces production's transaction lifecycle closely enough to catch that class of bug.
@DisplayName("RefreshController — обратная совместимость со старыми refresh token без claim jti")
class RefreshTokenLegacyCompatibilityIT extends AbstractIntegrationTest {

    private static final String EMAIL = "refresh-legacy-it@example.com";

    @Autowired
    private JwtConfig jwtConfig;

    @Autowired
    @Qualifier("authTransactionManager")
    private PlatformTransactionManager transactionManager;

    // A plain @Transactional here would not help: cleanup runs directly on the test instance
    // (never proxied by Spring), and the test method itself is deliberately not @Transactional —
    // so this uses a real, self-contained transaction to delete the leftover refresh tokens and
    // user row between tests.
    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                userRepository.findUserByEmail(EMAIL).ifPresent(user -> {
                    refreshTokenRepository.deleteAllByUser(user);
                    userRepository.delete(user);
                }));
    }

    @Test
    @DisplayName("Refresh token, выпущенный до появления claim jti, по-прежнему принимается — валидация jti не проверяет")
    void legacyRefreshTokenWithoutJtiClaimIsStillAccepted() throws Exception {
        User user = createUser();
        user.setEmail(EMAIL);
        userRepository.save(user);

        // Reproduces the pre-fix JwtService.generateRefreshToken exactly: userId + type claims,
        // no jti. Proves an already-issued token in the wild (no re-login forced) keeps working.
        String legacyRawToken = buildLegacyRefreshTokenWithoutJti(user);
        refreshTokenService.createRefreshToken(user, legacyRawToken, "Mozilla/5.0", "192.168.1.1");

        mockMvc.perform(post("/auth/api/refresh")
                        .cookie(new Cookie("refreshToken", legacyRawToken))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.accessToken").exists());
    }

    private String buildLegacyRefreshTokenWithoutJti(User user) {
        SecretKey key = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getRefreshTokenExpiration());

        return Jwts.builder()
                .claim("userId", user.getId().toString())
                .claim("type", "refresh")
                .subject(user.getEmail())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
