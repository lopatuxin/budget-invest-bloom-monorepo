package pyc.lopatuxin.auth;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import pyc.lopatuxin.security.config.CorsConfig;
import pyc.lopatuxin.security.config.JwtDecoderConfig;
import pyc.lopatuxin.security.config.SecurityConfig;

/**
 * Standalone Spring Boot entry point for the auth module's own integration tests.
 *
 * <p>The module is embedded in the monolith at runtime (see {@code pyc.lopatuxin.App} in the
 * {@code app} module), so it has no {@code @SpringBootApplication} class of its own in
 * {@code main}. The {@code app} module is not a dependency of {@code auth} (only the other way
 * around), so it cannot be reused here — {@link AbstractIntegrationTest} needs its own minimal
 * boot class to let {@code @SpringBootTest} find a configuration to start. Mirrors
 * {@code pyc.lopatuxin.budget.TestApplication} in the {@code budget} module.</p>
 *
 * <p>Unlike the monolith, this test context owns the whole (single-schema) datasource, so the
 * standard JPA/Liquibase auto-configuration is used as-is, without the manual multi-schema wiring
 * from {@code pyc.lopatuxin.config.AuthPersistenceConfig}. Auth services reference their
 * transaction manager by the explicit qualifier {@code "authTransactionManager"} (matching the
 * bean name wired in the monolith's {@code AuthPersistenceConfig}); the same bean is also
 * registered as the default {@code "transactionManager"} so repositories used directly by tests
 * (with no explicit qualifier) resolve it unambiguously.</p>
 *
 * <p>The {@code auth} module has no dependency on the {@code security} module (only the monolith's
 * {@code app} module wires both together), so an isolated test context has no security perimeter
 * beans of its own and falls back to Spring Security's fully-locked-down defaults. Importing the
 * real {@link SecurityConfig}, {@link JwtDecoderConfig} and {@link CorsConfig} here reproduces the
 * exact perimeter the monolith runs in production, so authorization checks in the controller tests
 * exercise real behaviour rather than a stand-in.</p>
 */
@SpringBootApplication
@Import({SecurityConfig.class, JwtDecoderConfig.class, CorsConfig.class})
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean(name = {"transactionManager", "authTransactionManager"})
    public PlatformTransactionManager authTransactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
