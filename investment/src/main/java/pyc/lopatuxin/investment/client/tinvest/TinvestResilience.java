package pyc.lopatuxin.investment.client.tinvest;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class TinvestResilience {

    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    private Retry retry;
    private CircuitBreaker circuitBreaker;

    // Once the token is rejected, every subsequent call short-circuits without touching the
    // network ("не долбить источник") until the app is restarted — the only way to pick up a
    // corrected TINVEST_TOKEN, since it is read once at startup.
    private final AtomicBoolean disabled = new AtomicBoolean(false);

    @PostConstruct
    void init() {
        this.retry = retryRegistry.retry("tinvest");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("tinvest");
    }

    public <T> T execute(String op, Supplier<T> action) {
        if (disabled.get()) {
            throw new TinvestUnauthorizedException("T-Invest отключён из-за отклонённого токена");
        }
        Supplier<T> decorated = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, action));
        try {
            return callWithRateLimitRetry(op, decorated);
        } catch (TinvestUnauthorizedException e) {
            if (disabled.compareAndSet(false, true)) {
                log.error("Токен T-Invest API отклонён, проверьте TINVEST_TOKEN");
            }
            throw e;
        } catch (Exception e) {
            log.warn("T-Invest {} fallback: {}", op, e.getMessage());
            throw new TinvestUnavailableException("T-Invest unavailable: " + e.getMessage(), e);
        }
    }

    // The 429 pause is driven by a response header the source sends on this specific call, not
    // by a fixed backoff — resilience4j's Retry config can only express fixed/exponential
    // waits, so it is configured to ignore TinvestRateLimitedException (see application.yml) and
    // the header-driven wait is handled here instead, with a single retry.
    private <T> T callWithRateLimitRetry(String op, Supplier<T> decorated) {
        try {
            return decorated.get();
        } catch (TinvestRateLimitedException e) {
            log.warn("T-Invest {} превысил лимит запросов, повтор через {} с", op, e.getRetryAfterSeconds());
            sleep(e.getRetryAfterSeconds());
            return decorated.get();
        }
    }

    private void sleep(long seconds) {
        try {
            Thread.sleep(Duration.ofSeconds(seconds));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
