package pyc.lopatuxin.auth.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.auth.config.JwtConfig;
import pyc.lopatuxin.auth.dto.request.RequestHeadersDto;
import pyc.lopatuxin.auth.dto.response.RefreshTokenResponse;
import pyc.lopatuxin.auth.entity.RefreshToken;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.exception.AccountInactiveException;
import pyc.lopatuxin.auth.exception.InvalidRefreshTokenException;
import pyc.lopatuxin.auth.exception.RefreshTokenExpiredException;
import pyc.lopatuxin.auth.exception.RefreshTokenReusedException;
import pyc.lopatuxin.auth.repository.RefreshTokenRepository;
import pyc.lopatuxin.auth.repository.UserRepository;
import pyc.lopatuxin.auth.util.RefreshTokenCookieHelper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenCookieHelper cookieHelper;
    private final PasswordEncoder tokenEncoder;
    private final JwtConfig jwtConfig;

    @Transactional
    public RefreshTokenResponse refreshTokens(
            String refreshToken,
            RequestHeadersDto headers,
            HttpServletResponse httpServletResponse) {

        log.info("Начало обновления токенов");

        UUID userId = validateAndExtractUserId(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Пользователь с ID {} не найден", userId);
                    return new EntityNotFoundException("Пользователь не найден");
                });

        validateUser(user);

        RefreshToken storedToken = findValidToken(refreshToken, user);
        validateRefreshToken(storedToken, user);

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);
        log.debug("Сгенерированы новые токены для пользователя {}", userId);

        markAsUsed(storedToken);
        createRefreshToken(user, newRefreshToken, headers.getUserAgent(), headers.extractIpAddress());
        cookieHelper.setRefreshTokenCookie(httpServletResponse, newRefreshToken);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Токены успешно обновлены для пользователя {}", userId);

        int expiresIn = (int) (jwtConfig.getAccessTokenExpiration() / 1000);
        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .build();
    }

    private UUID validateAndExtractUserId(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Отсутствует refresh token в запросе");
            throw new InvalidRefreshTokenException("Отсутствует обязательное поле refreshToken");
        }

        try {
            UUID userId = jwtService.extractUserId(refreshToken);
            log.debug("Извлечен userId: {} из refresh token", userId);

            return userId;
        } catch (ExpiredJwtException e) {
            log.warn("Refresh token истек: {}", e.getMessage());
            throw new RefreshTokenExpiredException("Refresh token истек. Требуется повторная аутентификация");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Недействительный refresh token: {}", e.getMessage());
            throw new InvalidRefreshTokenException("Refresh token имеет недействительную подпись или формат");
        }
    }

    private void validateUser(User user) {
        if (Boolean.FALSE.equals(user.getIsActive())) {
            log.warn("Попытка обновления токена для неактивного пользователя {}", user.getId());
            throw new AccountInactiveException("Аккаунт деактивирован. Обратитесь в службу поддержки");
        }

        LocalDateTime lockedUntil = user.getLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            log.warn("Попытка обновления токена для заблокированного пользователя {}", user.getId());
            throw new AccessDeniedException("Аккаунт заблокирован до " + lockedUntil);
        }
    }

    @Nullable
    public RefreshToken findValidToken(String token, User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findActiveTokensByUser(user, LocalDateTime.now());

        return activeTokens.stream()
                .filter(refreshToken -> tokenEncoder.matches(token, refreshToken.getTokenHash()))
                .findFirst()
                .orElse(null);
    }

    public void deleteAllUserTokens(User user) {
        refreshTokenRepository.deleteAllByUser(user);
        log.info("Удалены все токены пользователя {}", user.getId());
    }

    private void validateRefreshToken(RefreshToken storedToken, User user) {
        if (storedToken == null) {
            log.warn("Refresh token не найден в БД или уже истек для пользователя {}", user.getId());
            throw new InvalidRefreshTokenException("Refresh token не найден или истек");
        }

        if (Boolean.TRUE.equals(storedToken.getIsUsed())) {
            log.error("Обнаружено повторное использование refresh token для пользователя {}. Возможна компрометация", user.getId());
            deleteAllUserTokens(user);
            throw new RefreshTokenReusedException("Refresh token уже был использован. Возможна компрометация сессии. Все сессии завершены");
        }
    }

    public void markAsUsed(RefreshToken refreshToken) {
        if (refreshToken != null) {
            refreshToken.setIsUsed(true);
            refreshTokenRepository.save(refreshToken);
            log.debug("Токен {} помечен как использованный", refreshToken.getId());
        }
    }

    public void createRefreshToken(User user, String token, String userAgent, String ipAddress) {
        String tokenHash = tokenEncoder.encode(token);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtConfig.getRefreshTokenExpiration() / 1000);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .isUsed(false)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .createdAt(LocalDateTime.now())
                .build();

        refreshTokenRepository.save(refreshToken);
    }
}