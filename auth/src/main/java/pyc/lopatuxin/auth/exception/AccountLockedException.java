package pyc.lopatuxin.auth.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * Исключение, выбрасываемое при попытке входа в заблокированный аккаунт
 */
public class AccountLockedException extends AccessDeniedException {

    public AccountLockedException(String message) {
        super(message);
    }
}
