package pyc.lopatuxin.investment.client.tinvest;

public class TinvestUnauthorizedException extends RuntimeException {

    public TinvestUnauthorizedException(String message) {
        super(message, null, true, false);
    }
}
