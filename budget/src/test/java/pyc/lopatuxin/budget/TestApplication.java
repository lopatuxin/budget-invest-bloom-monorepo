package pyc.lopatuxin.budget;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import pyc.lopatuxin.budget.service.OverviewPageService;
import pyc.lopatuxin.shared.port.PortfolioCurrentValuation;
import pyc.lopatuxin.shared.port.PortfolioValuation;
import pyc.lopatuxin.shared.port.PortfolioValueAt;
import pyc.lopatuxin.shared.port.PortfolioValueSeries;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
 *
 * <p>{@link PortfolioValuation} is implemented in the {@code investment} module, which is not a
 * dependency of {@code budget} (only the other way around, same as {@code app} above) — so this
 * context needs its own zero-valuation stub for {@link OverviewPageService} to wire against.</p>
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

    @Bean
    public PortfolioValuation portfolioValuation() {
        return new PortfolioValuation() {
            @Override
            public PortfolioCurrentValuation current(UUID userId) {
                return new PortfolioCurrentValuation(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0,
                        BigDecimal.ZERO, null);
            }

            @Override
            public PortfolioValueSeries valueAt(UUID userId, List<LocalDate> dates) {
                return new PortfolioValueSeries(
                        dates.stream().map(date -> new PortfolioValueAt(date, BigDecimal.ZERO)).toList(),
                        false);
            }
        };
    }
}
