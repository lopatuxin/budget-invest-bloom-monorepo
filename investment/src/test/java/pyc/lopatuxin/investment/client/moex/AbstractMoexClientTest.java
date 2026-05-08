package pyc.lopatuxin.investment.client.moex;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

import java.util.Map;

/**
 * Base class for MoexIssClient unit tests.
 * Sets up MoexResilience (RetryRegistry + CircuitBreakerRegistry) and a mock MoexIssApi
 * so each subclass only declares its own @Test methods.
 */
class AbstractMoexClientTest {

    @Mock
    protected MoexIssApi api;

    protected MoexIssClient client;

    @BeforeEach
    void setUpMoexResilience() {
        RetryRegistry retryReg = RetryRegistry.of(Map.of(
                "moex", RetryConfig.custom().maxAttempts(1).build()));
        CircuitBreakerRegistry cbReg = CircuitBreakerRegistry.ofDefaults();
        MoexResilience resilience = new MoexResilience(retryReg, cbReg);
        resilience.init();
        client = new MoexIssClient(api, resilience);
    }
}
