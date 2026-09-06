package pyc.lopatuxin.budget.exception;

/**
 * Domain-level conflict: an operation forbidden by budget business rules
 * (e.g. modifying a protected system category, deleting a mirrored transfer record).
 * Unlike a bare {@link IllegalStateException}, this type is deliberately thrown
 * for cases whose message is safe to return to the client as-is.
 */
public class BudgetConflictException extends RuntimeException {

    public BudgetConflictException(String message) {
        super(message);
    }
}
