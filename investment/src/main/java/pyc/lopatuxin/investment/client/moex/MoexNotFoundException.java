package pyc.lopatuxin.investment.client.moex;

public class MoexNotFoundException extends RuntimeException {

    public MoexNotFoundException(String message) {
        super(message, null, true, false);
    }
}
