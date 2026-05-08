package pyc.lopatuxin.investment.client.moex;

public class MoexUnavailableException extends RuntimeException {

    public MoexUnavailableException(String message) {
        super(message, null, true, false);
    }

    public MoexUnavailableException(String message, Throwable cause) {
        super(message, cause, true, false);
    }
}
