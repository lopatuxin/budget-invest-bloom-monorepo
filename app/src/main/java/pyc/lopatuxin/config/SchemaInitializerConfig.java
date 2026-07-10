package pyc.lopatuxin.config;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Creates the three schemas (auth/budget/investment) on the shared "bib" database
// before any of the module-specific SpringLiquibase beans run (see @DependsOn on them).
@Configuration
public class SchemaInitializerConfig {

    @Bean
    public InitializingBean schemaInitializer(DataSource dataSource) {
        return () -> {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS auth");
                statement.execute("CREATE SCHEMA IF NOT EXISTS budget");
                statement.execute("CREATE SCHEMA IF NOT EXISTS investment");
            }
        };
    }
}
