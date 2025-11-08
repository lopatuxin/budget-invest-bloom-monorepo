package pyc.lopatuxin.auth.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * Исключение, выбрасываемое при обнаружении повторного использования refresh токена.
 * Это может указывать на компрометацию токена.
 */
public class RefreshTokenReusedException extends AccessDeniedException {

    public RefreshTokenReusedException(String message) {
        super(message);
    }
}