package pyc.lopatuxin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

import java.time.ZoneId;
import java.util.TimeZone;

// Three separate EntityManagerFactory/TransactionManager/SpringLiquibase beans are configured
// manually (one per schema: auth/budget/investment) in pyc.lopatuxin.config.*PersistenceConfig,
// so the single-datasource auto-configurations for JPA/Liquibase must be excluded.
// The DataSource auto-configuration itself stays enabled (shared spring.datasource).
@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class,
        LiquibaseAutoConfiguration.class
})
public class App {

    // Single timezone for the whole JVM: without it the container's clock defaults to UTC
    // and every LocalDate.now() (expenses, incomes) lands on the wrong day for hours around
    // midnight Moscow time. Set before the context starts so every module observes it.
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Moscow");

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(APP_ZONE));

        // auth/budget/investment modules each have same-simple-name classes (e.g. GlobalExceptionHandler);
        // FQN-based bean naming avoids bean name collisions between modules in the merged context.
        SpringApplication app = new SpringApplication(App.class);
        app.setBeanNameGenerator(FullyQualifiedAnnotationBeanNameGenerator.INSTANCE);
        app.run(args);
    }
}
