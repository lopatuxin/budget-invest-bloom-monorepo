package pyc.lopatuxin.budget;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Standalone Spring Boot entry point for the budget module's own integration tests.
 *
 * <p>The module is embedded in the monolith at runtime (see {@code pyc.lopatuxin.App} in the
 * {@code app} module), so it has no {@code @SpringBootApplication} class of its own in
 * {@code main}. The {@code app} module is not a dependency of {@code budget} (only the other
 * way around), so it cannot be reused here — {@link AbstractIntegrationTest} needs its own
 * minimal boot class to let {@code @SpringBootTest} find a configuration to start.</p>
 *
 * <p>Unlike the monolith, this test context owns the whole (single-schema) datasource, so the
 * standard JPA/Liquibase auto-configuration is used as-is, without the manual multi-schema
 * wiring from {@code pyc.lopatuxin.config.*PersistenceConfig}. Budget services reference their
 * transaction manager by the explicit qualifier {@code "budgetTransactionManager"} (matching the
 * bean name wired in the monolith's {@code BudgetPersistenceConfig}); the same bean is also
 * registered as the default {@code "transactionManager"} so repositories used directly by tests
 * (with no explicit qualifier) resolve it unambiguously.</p>
 */
@SpringBootApplication
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean(name = {"transactionManager", "budgetTransactionManager"})
    public PlatformTransactionManager budgetTransactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
