package pyc.lopatuxin.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import liquibase.integration.spring.SpringLiquibase;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableJpaRepositories(
        basePackages = "pyc.lopatuxin.budget.repository",
        entityManagerFactoryRef = "budgetEntityManagerFactory",
        transactionManagerRef = "budgetTransactionManager")
public class BudgetPersistenceConfig {

    @Bean
    @DependsOn("budgetLiquibase")
    public LocalContainerEntityManagerFactoryBean budgetEntityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("pyc.lopatuxin.budget.entity");
        emf.setPersistenceUnitName("budget");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.default_schema", "budget");
        props.put("hibernate.hbm2ddl.auto", "validate");
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        // CRITICAL: preserve camelCase->snake_case column mapping that Spring Boot applies by default.
        // Since HibernateJpaAutoConfiguration is excluded, set naming strategies explicitly
        // (values match Spring Boot 4's own defaults, see HibernateProperties.Naming).
        props.put("hibernate.implicit_naming_strategy", "org.springframework.boot.hibernate.SpringImplicitNamingStrategy");
        props.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
        emf.setJpaPropertyMap(props);
        return emf;
    }

    @Bean
    public PlatformTransactionManager budgetTransactionManager(
            @Qualifier("budgetEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean
    @DependsOn("schemaInitializer")
    public SpringLiquibase budgetLiquibase(DataSource dataSource) {
        SpringLiquibase lb = new SpringLiquibase();
        lb.setDataSource(dataSource);
        lb.setChangeLog("classpath:db/changelog/budget/db.changelog-master.yml");
        lb.setDefaultSchema("budget");
        lb.setLiquibaseSchema("budget");
        lb.setContexts("data-migration");
        return lb;
    }
}
