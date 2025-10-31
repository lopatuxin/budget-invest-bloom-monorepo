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
import pyc.lopatuxin.auth.dto.request.UserContext;
import pyc.lopatuxin.auth.entity.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
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
    private final JwtConfig jwtConfig;

    /**
     * Генерация access токена для пользователя
     *
     * @param user пользователь
     * @return JWT access токен
     */
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, user.getId().toString());
        claims.put("email", user.getEmail());
        claims.put("username", user.getUsername());

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
     * Извлечение username из токена
     *
     * @param token JWT токен
     * @return username пользователя
     */
    public String extractUsername(String token) {
        return extractClaim(token, claims -> claims.get("username", String.class));
    }

    /**
     * Извлечение UserContext из токена
     *
     * @param token JWT токен
     * @return UserContext с данными пользователя или пустой UserContext в случае ошибки
     */
    public UserContext extractUserContext(String token) {
        try {
            if (token == null || token.isBlank()) {
                return UserContext.builder().build();
            }

            String email = extractEmail(token);
            String username = extractUsername(token);
            UUID userId = extractUserId(token);

            return UserContext.builder()
                    .userId(userId)
                    .email(email)
                    .username(username)
                    .build();
        } catch (Exception e) {
            log.error("Ошибка при извлечении данных пользователя из JWT токена", e);
            return UserContext.builder().build();
        }
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