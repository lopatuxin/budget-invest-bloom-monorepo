package pyc.lopatuxin.investment;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import pyc.lopatuxin.shared.port.EntryType;
import pyc.lopatuxin.shared.port.InvestmentBudgetSync;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Standalone Spring Boot entry point for the investment module's own integration tests.
 *
 * <p>The module is embedded in the monolith at runtime (see {@code pyc.lopatuxin.App} in the
 * {@code app} module), so it has no {@code @SpringBootApplication} class of its own in
 * {@code main}. The {@code app} module is not a dependency of {@code investment} (only the
 * other way around), so it cannot be reused here — {@link AbstractIntegrationTest} needs its
 * own minimal boot class to let {@code @SpringBootTest} find a configuration to start. Mirrors
 * {@code pyc.lopatuxin.budget.TestApplication} in the {@code budget} module.</p>
 *
 * <p>Unlike the monolith, this test context owns the whole (single-schema) datasource, so the
 * standard JPA/Liquibase auto-configuration is used as-is. Investment services reference their
 * transaction manager by the explicit qualifier {@code "investmentTransactionManager"} (matching
 * the bean name wired in the monolith's persistence config); the same bean is also registered as
 * the default {@code "transactionManager"} so repositories used directly by tests resolve it
 * unambiguously.</p>
 *
 * <p>{@link InvestmentBudgetSync} is implemented in the {@code budget} module, which is not a
 * dependency of {@code investment} (only the other way around, same as {@code app} above) — so
 * this context needs its own no-op stub for {@code TransactionService} to wire against.</p>
 */
@SpringBootApplication
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean(name = {"transactionManager", "investmentTransactionManager"})
    public PlatformTransactionManager investmentTransactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    public InvestmentBudgetSync investmentBudgetSync() {
        return new InvestmentBudgetSync() {
            @Override
            public UUID createEntry(UUID userId, EntryType type, BigDecimal amount, Instant executedAt) {
                return UUID.randomUUID();
            }

            @Override
            public void deleteEntry(UUID userId, UUID budgetEntryId, EntryType type) {
                // no-op: nothing to roll back in an isolated investment test context
            }
        };
    }
}
