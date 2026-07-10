package pyc.lopatuxin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

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
    public static void main(String[] args) {
        // auth/budget/investment modules each have same-simple-name classes (e.g. GlobalExceptionHandler);
        // FQN-based bean naming avoids bean name collisions between modules in the merged context.
        SpringApplication app = new SpringApplication(App.class);
        app.setBeanNameGenerator(FullyQualifiedAnnotationBeanNameGenerator.INSTANCE);
        app.run(args);
    }
}
