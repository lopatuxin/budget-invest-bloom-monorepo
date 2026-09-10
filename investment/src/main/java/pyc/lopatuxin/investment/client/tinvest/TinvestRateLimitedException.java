package pyc.lopatuxin.investment.client.tinvest;

import lombok.Getter;

@Getter
public class TinvestRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public TinvestRateLimitedException(long retryAfterSeconds) {
        super("T-Invest rate limit exceeded, retry after " + retryAfterSeconds + "s", null, true, false);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
