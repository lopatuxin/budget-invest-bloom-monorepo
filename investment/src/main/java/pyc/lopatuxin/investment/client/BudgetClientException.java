package pyc.lopatuxin.investment.client;

public class BudgetClientException extends RuntimeException {

    private final int statusCode;

    public BudgetClientException(String message) {
        super(message, null, true, false);
        this.statusCode = 0;
    }

    public BudgetClientException(String message, Throwable cause) {
        super(message, cause, true, false);
        this.statusCode = 0;
    }

    public BudgetClientException(String message, int statusCode) {
        super(message, null, true, false);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
