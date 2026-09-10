package pyc.lopatuxin.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.auth.config.JwtConfig;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.entity.UserRole;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Сервис для работы с JWT токенами
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_JTI = "jti";
    private final JwtConfig jwtConfig;

    /**
     * Генерация access токена для пользователя
     *
     * @param user пользователь
     * @return JWT access токен
     */
    public String generateAccessToken(User user) {
        List<UserRole> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            // findUserByEmail's LEFT JOIN FETCH u.roles guarantees roles is loaded (never lazy),
            // so an empty list here means the user genuinely has no role row — a data-integrity
            // problem, not something a client request can cause. Fail loudly with the user's id
            // instead of letting List.getFirst() throw a bare NoSuchElementException.
            throw new IllegalStateException("У пользователя " + user.getId() + " нет ни одной роли");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, user.getId().toString());
        claims.put("email", user.getEmail());
        claims.put("username", user.getUsername());
        claims.put("role", roles.getFirst().getRoleName().name());

        return generateToken(claims, user.getEmail(), jwtConfig.getAccessTokenExpiration());
    }

    /**
     * Генерация refresh токена для пользователя
     *
     * @param user пользователь
     * @return JWT refresh токен
     */
    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, user.getId().toString());
        claims.put("type", "refresh");
        // Random unique id — the only thing distinguishing two refresh tokens minted for the same
        // user in the same second, since issuedAt is truncated to seconds and every other claim is
        // deterministic. Not read back anywhere: it exists solely so the raw JWT (and therefore its
        // hash in refresh_tokens) is never accidentally identical to another live token. An
        // already-issued token without this claim keeps working — nothing validates its presence.
        claims.put(CLAIM_JTI, UUID.randomUUID().toString());

        return generateToken(claims, user.getEmail(), jwtConfig.getRefreshTokenExpiration());
    }

    /**
     * Извлечение email из токена
     *
     * @param token JWT токен
     * @return email пользователя
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Извлечение userId из токена
     *
     * @param token JWT токен
     * @return UUID пользователя
     */
    public UUID extractUserId(String token) {
        String userId = extractClaim(token, claims -> claims.get(CLAIM_USER_ID, String.class));
        return UUID.fromString(userId);
    }

    /**
     * Извлечение даты истечения из токена
     *
     * @param token JWT токен
     * @return дата истечения
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Проверка валидности токена
     *
     * @param token JWT токен
     * @param email email пользователя для проверки
     * @return true если токен валидный
     */
    public boolean isTokenValid(String token, String email) {
        try {
            final String tokenEmail = extractEmail(token);
            return (tokenEmail.equals(email) && !isTokenExpired(token));
        } catch (ExpiredJwtException e) {
            log.debug("Токен истек: {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            log.warn("Неверная подпись токена: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.warn("Некорректный формат токена: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            log.warn("Неподдерживаемый токен: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("Пустой токен: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Ошибка валидации токена", e);
            return false;
        }
    }

    /**
     * Проверка истечения токена
     *
     * @param token JWT токен
     * @return true если токен истек
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Извлечение конкретного claim из токена
     *
     * @param token          JWT токен
     * @param claimsResolver функция для извлечения claim
     * @param <T>            тип возвращаемого значения
     * @return значение claim
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Извлечение всех claims из токена
     *
     * @param token JWT токен
     * @return все claims
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Генерация токена
     *
     * @param extraClaims    дополнительные claims
     * @param subject        subject токена (обычно email)
     * @param expirationTime время жизни токена в миллисекундах
     * @return JWT токен
     */
    private String generateToken(Map<String, Object> extraClaims, String subject, Long expirationTime) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Получение ключа для подписи токенов
     *
     * @return секретный ключ
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}