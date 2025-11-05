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
        cookie.setSecure(true);    // Только по HTTPS
        cookie.setPath("/api/auth"); // Доступен только для auth endpoints
        cookie.setMaxAge((int) (jwtConfig.getRefreshTokenExpiration() / 1000)); // 7 дней
        cookie.setAttribute("SameSite", "Strict"); // Защита от CSRF
        response.addCookie(cookie);
    }
}