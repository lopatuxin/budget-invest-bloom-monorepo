package pyc.lopatuxin.auth.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pyc.lopatuxin.auth.config.JwtConfig;

/**
 * Вспомогательный класс для работы с refresh token cookie.
 * Обеспечивает единообразную установку безопасных httpOnly cookies.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieHelper {

    private final JwtConfig jwtConfig;

    /**
     * Устанавливает refresh token в httpOnly cookie с настройками безопасности.
     *
     * @param response     HTTP ответ
     * @param refreshToken refresh token для установки
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);  // Защита от XSS

        // Настройки из конфигурации
        JwtConfig.CookieSettings cookieSettings = jwtConfig.getCookie();
        cookie.setSecure(cookieSettings.getSecure());
        cookie.setPath(cookieSettings.getPath());
        cookie.setMaxAge((int) (jwtConfig.getRefreshTokenExpiration() / 1000));
        cookie.setAttribute("SameSite", cookieSettings.getSameSite());

        // Установка домена, если указан
        if (cookieSettings.getDomain() != null && !cookieSettings.getDomain().isBlank()) {
            cookie.setDomain(cookieSettings.getDomain());
        }

        response.addCookie(cookie);
    }

    /**
     * Удаляет refresh token cookie из браузера.
     *
     * @param response HTTP ответ
     */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);  // Удаление cookie

        // Применяем те же настройки, что и при создании
        JwtConfig.CookieSettings cookieSettings = jwtConfig.getCookie();
        cookie.setSecure(cookieSettings.getSecure());
        cookie.setPath(cookieSettings.getPath());
        cookie.setAttribute("SameSite", cookieSettings.getSameSite());

        if (cookieSettings.getDomain() != null && !cookieSettings.getDomain().isBlank()) {
            cookie.setDomain(cookieSettings.getDomain());
        }

        response.addCookie(cookie);
    }
}