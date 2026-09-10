package pyc.lopatuxin.investment.client.tinvest;

public class TinvestUnavailableException extends RuntimeException {

    public TinvestUnavailableException(String message, Throwable cause) {
        super(message, cause, true, false);
    }
}
