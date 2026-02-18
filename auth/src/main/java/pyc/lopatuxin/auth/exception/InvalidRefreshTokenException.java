package pyc.lopatuxin.auth.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Исключение, выбрасываемое при попытке использовать недействительный refresh токен
 */
public class InvalidRefreshTokenException extends AuthenticationException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }

    public InvalidRefreshTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}