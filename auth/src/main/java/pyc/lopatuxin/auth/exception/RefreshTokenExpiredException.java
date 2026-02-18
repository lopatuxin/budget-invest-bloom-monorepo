package pyc.lopatuxin.auth.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Исключение, выбрасываемое при попытке использовать истекший refresh токен
 */
public class RefreshTokenExpiredException extends AuthenticationException {

    public RefreshTokenExpiredException(String message) {
        super(message);
    }

    public RefreshTokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}