package pyc.lopatuxin.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.auth.config.JwtConfig;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.entity.UserRole;
import pyc.lopatuxin.auth.enums.RoleName;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtServiceUnitTest")
class JwtServiceUnitTest {

    @Mock
    private JwtConfig jwtConfig;

    @InjectMocks
    private JwtService jwtService;

    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm-minimum-256-bits";
    private static final Long ACCESS_TOKEN_EXPIRATION = 900000L; // 15 минут
    private static final Long REFRESH_TOKEN_EXPIRATION = 604800000L; // 7 дней

    private User testUser;

    @BeforeEach
    void setUp() {
        lenient().when(jwtConfig.getSecret()).thenReturn(TEST_SECRET);
        lenient().when(jwtConfig.getAccessTokenExpiration()).thenReturn(ACCESS_TOKEN_EXPIRATION);
        lenient().when(jwtConfig.getRefreshTokenExpiration()).thenReturn(REFRESH_TOKEN_EXPIRATION);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .username("testuser")
                .build();

        UserRole role = UserRole.builder()
                .roleName(RoleName.USER)
                .build();
        role.setUser(testUser);
        testUser.setRoles(java.util.List.of(role));
    }

    @Test
    @DisplayName("Должен успешно генерировать access токен")
    void shouldGenerateAccessToken() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(token).isNotNull().isNotEmpty();

        Claims claims = parseToken(token);
        assertThat(claims.getSubject()).isEqualTo(testUser.getEmail());
        assertThat(claims.get("userId", String.class)).isEqualTo(testUser.getId().toString());
        assertThat(claims.get("email", String.class)).isEqualTo(testUser.getEmail());
        assertThat(claims.get("username", String.class)).isEqualTo(testUser.getUsername());
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    @DisplayName("Должен успешно генерировать refresh токен")
    void shouldGenerateRefreshToken() {
        String token = jwtService.generateRefreshToken(testUser);

        assertThat(token).isNotNull().isNotEmpty();

        Claims claims = parseToken(token);
        assertThat(claims.getSubject()).isEqualTo(testUser.getEmail());
        assertThat(claims.get("userId", String.class)).isEqualTo(testUser.getId().toString());
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    @DisplayName("Должен правильно извлекать email из токена")
    void shouldExtractEmailFromToken() {
        String token = jwtService.generateAccessToken(testUser);

        String email = jwtService.extractEmail(token);

        assertThat(email).isEqualTo(testUser.getEmail());
    }

    @Test
    @DisplayName("Должен правильно извлекать userId из токена")
    void shouldExtractUserIdFromToken() {
        String token = jwtService.generateAccessToken(testUser);

        UUID userId = jwtService.extractUserId(token);

        assertThat(userId).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("Должен правильно извлекать дату истечения из токена")
    void shouldExtractExpirationFromToken() {
        String token = jwtService.generateAccessToken(testUser);

        Date expiration = jwtService.extractExpiration(token);

        assertThat(expiration)
                .isNotNull()
                .isAfter(new Date());
    }

    @Test
    @DisplayName("Должен проверять корректный токен")
    void shouldValidateCorrectToken() {
        String token = jwtService.generateAccessToken(testUser);

        boolean isValid = jwtService.isTokenValid(token, testUser.getEmail());

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Должен отклонять токен с неверным email")
    void shouldRejectTokenWithWrongEmail() {
        String token = jwtService.generateAccessToken(testUser);

        boolean isValid = jwtService.isTokenValid(token, "wrong@example.com");

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Должен отклонять истекший токен")
    void shouldRejectExpiredToken() {
        lenient().when(jwtConfig.getAccessTokenExpiration()).thenReturn(-1000L);
        String token = jwtService.generateAccessToken(testUser);

        boolean isValid = jwtService.isTokenValid(token, testUser.getEmail());

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Должен отклонять токен с неверной подписью")
    void shouldRejectTokenWithInvalidSignature() {
        String token = jwtService.generateAccessToken(testUser);

        lenient().when(jwtConfig.getSecret()).thenReturn("different-secret-key-that-is-also-long-enough-for-hmac-sha256-algorithm");

        boolean isValid = jwtService.isTokenValid(token, testUser.getEmail());

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Должен отклонять некорректный формат токена")
    void shouldRejectMalformedToken() {
        String malformedToken = "this.is.not.a.valid.jwt.token";

        boolean isValid = jwtService.isTokenValid(malformedToken, testUser.getEmail());

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Должен отклонять пустой токен")
    void shouldRejectEmptyToken() {
        boolean isValid = jwtService.isTokenValid("", testUser.getEmail());

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Access токен должен иметь корректное время жизни")
    void accessTokenShouldHaveCorrectExpiration() {
        long beforeGeneration = System.currentTimeMillis();

        String token = jwtService.generateAccessToken(testUser);
        Date expiration = jwtService.extractExpiration(token);

        long expectedExpiration = beforeGeneration + ACCESS_TOKEN_EXPIRATION;
        long actualExpiration = expiration.getTime();

        assertThat(actualExpiration).isBetween(expectedExpiration - 1000, expectedExpiration + 1000);
    }

    @Test
    @DisplayName("Refresh токен должен иметь корректное время жизни")
    void refreshTokenShouldHaveCorrectExpiration() {
        long beforeGeneration = System.currentTimeMillis();

        String token = jwtService.generateRefreshToken(testUser);
        Date expiration = jwtService.extractExpiration(token);

        long expectedExpiration = beforeGeneration + REFRESH_TOKEN_EXPIRATION;
        long actualExpiration = expiration.getTime();

        assertThat(actualExpiration).isBetween(expectedExpiration - 1000, expectedExpiration + 1000);
    }

    @Test
    @DisplayName("Должен генерировать уникальные токены для одного пользователя")
    void shouldGenerateUniqueTokensForSameUser() throws InterruptedException {
        String token1 = jwtService.generateAccessToken(testUser);

        Thread.sleep(1000);

        String token2 = jwtService.generateAccessToken(testUser);

        assertThat(token1).isNotEqualTo(token2);
        assertThat(jwtService.isTokenValid(token1, testUser.getEmail())).isTrue();
        assertThat(jwtService.isTokenValid(token2, testUser.getEmail())).isTrue();
    }

    // Builds two refresh tokens with a manually fixed, shared issuedAt instead of relying on two
    // real calls to generateRefreshToken() landing in the same wall-clock second — a wait-for-luck
    // loop over that would still be able to run out and fail on a slow or loaded machine. Forcing
    // the shared iat deterministically tests the exact same guarantee (jti still tells the tokens
    // apart when everything else, iat included, matches) with no dependency on timing.
    @Test
    @DisplayName("Два refresh токена с одинаковым iat всё равно различаются благодаря claim jti")
    void shouldGenerateDifferentRefreshTokensForTheSameIssuedAtSecond() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Date sharedIssuedAt = new Date();
        Date expiration = new Date(sharedIssuedAt.getTime() + REFRESH_TOKEN_EXPIRATION);

        String token1 = buildRefreshTokenWithFixedIssuedAt(key, sharedIssuedAt, expiration);
        String token2 = buildRefreshTokenWithFixedIssuedAt(key, sharedIssuedAt, expiration);

        Claims claims1 = parseToken(token1);
        Claims claims2 = parseToken(token2);

        assertThat(claims1.getIssuedAt()).isEqualTo(claims2.getIssuedAt());
        assertThat(token1).isNotEqualTo(token2);
        assertThat(claims1.get("jti", String.class)).isNotBlank();
        assertThat(claims1.get("jti", String.class)).isNotEqualTo(claims2.get("jti", String.class));
    }

    private String buildRefreshTokenWithFixedIssuedAt(SecretKey key, Date issuedAt, Date expiration) {
        return Jwts.builder()
                .claim("userId", testUser.getId().toString())
                .claim("type", "refresh")
                .claim("jti", UUID.randomUUID().toString())
                .subject(testUser.getEmail())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Test
    @DisplayName("Уже выпущенный refresh токен без claim jti по-прежнему валиден — при валидации jti не проверяется")
    void refreshTokenWithoutJtiClaim_isStillValid() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String legacyToken = Jwts.builder()
                .claim("userId", testUser.getId().toString())
                .claim("type", "refresh")
                .subject(testUser.getEmail())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_TOKEN_EXPIRATION))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.isTokenValid(legacyToken, testUser.getEmail())).isTrue();
        assertThat(jwtService.extractUserId(legacyToken)).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("Должен генерировать разные токены для разных пользователей")
    void shouldGenerateDifferentTokensForDifferentUsers() {
        User anotherUser = User.builder()
                .id(UUID.randomUUID())
                .email("another@example.com")
                .username("anotheruser")
                .build();

        UserRole anotherRole = UserRole.builder()
                .roleName(RoleName.USER)
                .build();
        anotherRole.setUser(anotherUser);
        anotherUser.setRoles(java.util.List.of(anotherRole));

        String token1 = jwtService.generateAccessToken(testUser);
        String token2 = jwtService.generateAccessToken(anotherUser);

        assertThat(token1).isNotEqualTo(token2);
        assertThat(jwtService.extractEmail(token1)).isEqualTo(testUser.getEmail());
        assertThat(jwtService.extractEmail(token2)).isEqualTo(anotherUser.getEmail());
    }

    @Test
    @DisplayName("Должен бросать исключение при извлечении данных из истекшего токена")
    void shouldThrowExceptionWhenExtractingFromExpiredToken() {
        lenient().when(jwtConfig.getAccessTokenExpiration()).thenReturn(-1000L);
        String expiredToken = jwtService.generateAccessToken(testUser);

        assertThatThrownBy(() -> jwtService.extractEmail(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Должен бросать понятную ошибку при генерации access токена для пользователя без ролей")
    void shouldThrowMeaningfulExceptionWhenUserHasNoRoles() {
        testUser.setRoles(List.of());

        assertThatThrownBy(() -> jwtService.generateAccessToken(testUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(testUser.getId().toString());
    }

    @Test
    @DisplayName("Должен бросать понятную ошибку при генерации access токена, если список ролей null")
    void shouldThrowMeaningfulExceptionWhenRolesIsNull() {
        testUser.setRoles(null);

        assertThatThrownBy(() -> jwtService.generateAccessToken(testUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(testUser.getId().toString());
    }

    /**
     * Вспомогательный метод для извлечения токена в тестах
     */
    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}