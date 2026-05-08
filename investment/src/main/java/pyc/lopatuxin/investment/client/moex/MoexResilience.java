package pyc.lopatuxin.investment.client.moex;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class MoexResilience {

    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    private Retry retry;
    private CircuitBreaker circuitBreaker;

    @PostConstruct
    void init() {
        this.retry = retryRegistry.retry("moex");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("moex");
    }

    public <T> T execute(String op, Supplier<T> action) {
        Supplier<T> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, action));
        try {
            return decorated.get();
        } catch (MoexNotFoundException e) {
            // 404 — expected on hot path (bonds market), propagate without warn-logging
            throw e;
        } catch (HttpClientErrorException e) {
            // other 4xx propagated as-is
            throw e;
        } catch (Exception e) {
            log.warn("MOEX {} fallback: {}", op, e.getMessage());
            throw new MoexUnavailableException("MOEX unavailable: " + e.getMessage(), e);
        }
    }
}
