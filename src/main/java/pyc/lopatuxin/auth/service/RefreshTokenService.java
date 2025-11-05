package pyc.lopatuxin.auth.service;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.auth.config.JwtConfig;
import pyc.lopatuxin.auth.dto.request.RequestHeadersDto;
import pyc.lopatuxin.auth.dto.response.RefreshTokenResponse;
import pyc.lopatuxin.auth.entity.RefreshToken;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.repository.RefreshTokenRepository;
import pyc.lopatuxin.auth.util.RefreshTokenCookieHelper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для работы с refresh токенами
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCookieHelper cookieHelper;
    private final PasswordEncoder tokenEncoder;
    private final JwtConfig jwtConfig;

    /**
     * Создание и сохранение refresh токена
     *
     * @param user      пользователь
     * @param token     сгенерированный токен
     * @param userAgent User-Agent клиента
     * @param ipAddress IP адрес клиента
     */
    @Transactional
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

    /**
     * Проверка и получение refresh токена
     *
     * @param token токен для проверки
     * @param user  пользователь
     * @return RefreshToken если найден и валиден
     */
    @Nullable
    @Transactional(readOnly = true)
    public RefreshToken findValidToken(String token, User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findActiveTokensByUser(user, LocalDateTime.now());

        for (RefreshToken refreshToken : activeTokens) {
            if (tokenEncoder.matches(token, refreshToken.getTokenHash())) {
                return refreshToken;
            }
        }

        return null;
    }

    /**
     * Пометить токен как использованный
     *
     * @param refreshToken токен для пометки
     */
    @Transactional
    public void markAsUsed(RefreshToken refreshToken) {
        refreshToken.setIsUsed(true);
        refreshTokenRepository.save(refreshToken);
        log.debug("Токен {} помечен как использованный", refreshToken.getId());
    }

    /**
     * Удалить все токены пользователя
     *
     * @param user пользователь
     */
    @Transactional
    public void deleteAllUserTokens(User user) {
        refreshTokenRepository.deleteAllByUser(user);
        log.info("Удалены все токены пользователя {}", user.getId());
    }

    /**
     * Очистка истекших и использованных токенов
     */
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredAndUsedTokens(LocalDateTime.now());
        log.debug("Выполнена очистка истекших и использованных токенов");
    }

    /**
     * Обновление токенов (заглушка)
     * TODO: Реализовать полную логику согласно спецификации POST_refresh.md
     *
     * @param refreshToken текущий refresh token
     * @return null (заглушка)
     */
    @Transactional
    public RefreshTokenResponse refreshTokens(
            String refreshToken,
            RequestHeadersDto headers,
            HttpServletResponse httpServletResponse) {

        log.info("Вызов метода refreshTokens (заглушка)");
        // TODO: Реализовать логику:
        // 1. Валидация refresh token
        // 2. Верификация подписи и срока действия
        // 3. Валидация пользователя и сессии
        // 4. Генерация новых access и refresh токенов
        // 5. Обновление сессионных данных
        // 6. Возврат новых токенов

        return null;
    }
}