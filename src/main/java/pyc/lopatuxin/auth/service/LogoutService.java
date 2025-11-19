package pyc.lopatuxin.auth.service;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.auth.dto.request.ApiRequest;
import pyc.lopatuxin.auth.dto.request.LogoutRequest;
import pyc.lopatuxin.auth.dto.request.RequestHeadersDto;
import pyc.lopatuxin.auth.dto.response.LogoutResponse;
import pyc.lopatuxin.auth.entity.RefreshToken;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.repository.RefreshTokenRepository;
import pyc.lopatuxin.auth.repository.UserRepository;
import pyc.lopatuxin.auth.util.RefreshTokenCookieHelper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final RefreshTokenCookieHelper cookieHelper;

    /**
     * Метод для выхода пользователя из системы.
     * Реализует следующую логику согласно спецификации:
     * 1. Получение email пользователя из JWT токена
     * 2. Валидация и обработка refresh token из запроса
     * 3. Отзыв токенов (удаление из БД)
     * 4. Обработка режима logoutFromAll (завершение всех сессий пользователя)
     * 5. Обновление last_logout_at и security_version в БД
     * 6. Удаление refresh token cookie из браузера
     * @param headers HTTP заголовки запроса
     * @param request запрос с данными logout
     * @param refreshTokenFromCookie refresh token из cookie
     * @param httpResponse HTTP ответ для удаления cookie
     * @return ответ с результатом выхода
     */
    @Transactional
    public LogoutResponse logout(RequestHeadersDto headers, ApiRequest<LogoutRequest> request, String refreshTokenFromCookie, HttpServletResponse httpResponse) {
        LogoutRequest data = request.getData();
        String email = jwtService.extractEmail(headers.getJwt());
        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        log.info("Запрос на выход от пользователя: {} (ID: {}). LogoutFromAll: {}",
                user.getEmail(), user.getId(), data.getLogoutFromAll());

        int loggedOutCount;

        if (Boolean.TRUE.equals(data.getLogoutFromAll())) {
            loggedOutCount = handleLogoutFromAll(user, headers.extractIpAddress(), headers.getUserAgent());
        } else {
            loggedOutCount = handleSingleLogout(user, refreshTokenFromCookie, headers.extractIpAddress());
        }

        user.setLastLogoutAt(LocalDateTime.now());
        userRepository.save(user);

        // Удаляем refresh token cookie из браузера
        cookieHelper.clearRefreshTokenCookie(httpResponse);

        String message = Boolean.TRUE.equals(data.getLogoutFromAll())
                ? "Вы успешно вышли из системы на всех устройствах"
                : "Вы успешно вышли из системы";

        log.info("Пользователь {} успешно вышел из системы. Завершено сессий: {}", user.getEmail(), loggedOutCount);

        return LogoutResponse.builder()
                .message(message)
                .loggedOut(loggedOutCount)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Обработка выхода со всех устройств (force logout)
     *
     * @param user пользователь
     * @param ipAddress IP адрес клиента
     * @param userAgent User-Agent клиента
     * @return количество завершенных сессий
     */
    private int handleLogoutFromAll(User user, String ipAddress, String userAgent) {
        log.info("Обработка выхода со всех устройств для пользователя: {} с IP: {}, User-Agent: {}",
                user.getEmail(), ipAddress, userAgent);

        List<RefreshToken> activeTokens = refreshTokenRepository.findActiveTokensByUser(user, LocalDateTime.now());
        int sessionCount = activeTokens.size();

        refreshTokenRepository.deleteAllByUser(user);

        user.setSecurityVersion(user.getSecurityVersion() + 1);
        userRepository.save(user);

        log.info("Удалено {} активных refresh токенов для пользователя: {}, версия безопасности обновлена до: {}",
                sessionCount, user.getEmail(), user.getSecurityVersion());

        return Math.max(sessionCount, 1);
    }

    /**
     * Обработка выхода из текущей сессии
     *
     * @param user пользователь
     * @param refreshTokenString refresh token из запроса
     * @param ipAddress IP адрес клиента
     * @return количество завершенных сессий (0 или 1)
     * @throws IllegalArgumentException если refresh token не передан
     */
    private int handleSingleLogout(User user, String refreshTokenString, String ipAddress) {
        log.debug("Обработка выхода из текущей сессии для пользователя: {} с IP: {}", user.getEmail(), ipAddress);

        if (refreshTokenString == null || refreshTokenString.isBlank()) {
            log.warn("Попытка выхода без refresh token для пользователя: {}", user.getEmail());
            throw new IllegalArgumentException(
                    "Для выхода из текущей сессии необходимо передать refresh token в cookie refreshToken. " +
                            "Используйте logoutFromAll: true для выхода со всех устройств без токена."
            );
        }

        RefreshToken refreshToken = refreshTokenService.findValidToken(refreshTokenString, user);

        if (refreshToken == null) {
            log.debug("Refresh токен не найден - возможно повторный вызов logout для пользователя: {}", user.getEmail());
            return 0;
        }

        refreshTokenRepository.delete(refreshToken);
        log.debug("Refresh токен удален для пользователя: {}", user.getEmail());
        return 1;
    }
}