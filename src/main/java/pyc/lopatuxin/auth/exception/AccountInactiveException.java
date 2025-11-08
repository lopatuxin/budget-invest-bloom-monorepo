package pyc.lopatuxin.auth.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * Исключение, выбрасываемое при попытке доступа к неактивному аккаунту
 */
public class AccountInactiveException extends AccessDeniedException {

    public AccountInactiveException(String message) {
        super(message);
    }
}