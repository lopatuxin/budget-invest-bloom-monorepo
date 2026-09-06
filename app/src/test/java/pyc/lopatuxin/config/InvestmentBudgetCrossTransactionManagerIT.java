package pyc.lopatuxin.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import pyc.lopatuxin.budget.dto.request.InvestmentEntryRequestDto;
import pyc.lopatuxin.budget.dto.response.InvestmentEntryResponseDto;
import pyc.lopatuxin.budget.service.InvestmentEntryService;
import pyc.lopatuxin.shared.port.EntryType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Reproduces the reported 500 on POST /api/investment/transactions: the investment module
 * runs its transaction under {@code investmentTransactionManager} and, mid-transaction, calls
 * into the budget module's {@link InvestmentEntryService}, which runs under a separate
 * {@code budgetTransactionManager} — a different {@link org.springframework.orm.jpa.JpaTransactionManager}
 * bean, but backed by the very same physical {@link javax.sql.DataSource} (one Postgres
 * database, three schemas). Before the fix this nested call failed with
 * {@code IllegalTransactionStateException: Pre-bound JDBC Connection found!} because both
 * {@link BudgetPersistenceConfig} and {@link InvestmentPersistenceConfig} exposed the shared
 * DataSource via {@code LocalContainerEntityManagerFactoryBean.setDataSource(...)}, which made
 * {@code JpaTransactionManager} try to coordinate it as a JDBC resource across the two
 * independent managers.
 */
@SpringBootTest(classes = InvestmentBudgetCrossTransactionManagerIT.TestApp.class)
@Testcontainers
@DisplayName("Совместная работа budgetTransactionManager и investmentTransactionManager на одном DataSource")
class InvestmentBudgetCrossTransactionManagerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("bib")
            .withUsername("bib")
            .withPassword("bib");

    @Autowired
    private InvestmentEntryService investmentEntryService;

    @Autowired
    @Qualifier("investmentTransactionManager")
    private PlatformTransactionManager investmentTransactionManager;

    @Test
    @DisplayName("Создание записи бюджета из InvestmentEntryService, вызванного внутри транзакции investmentTransactionManager, не падает с IllegalTransactionStateException")
    void createsBudgetEntryFromNestedInvestmentTransaction() {
        UUID userId = UUID.randomUUID();
        InvestmentEntryRequestDto dto = InvestmentEntryRequestDto.builder()
                .type(EntryType.BUY)
                .amount(new BigDecimal("2505.00"))
                .executedAt(Instant.now())
                .build();

        TransactionTemplate investmentTx = new TransactionTemplate(investmentTransactionManager);

        InvestmentEntryResponseDto[] result = new InvestmentEntryResponseDto[1];
        assertThatCode(() -> result[0] = investmentTx.execute(status -> investmentEntryService.create(userId, dto)))
                .doesNotThrowAnyException();
        assertThat(result[0]).isNotNull();
        assertThat(result[0].getEntryId()).isNotNull();
    }

    @Configuration
    @EnableAutoConfiguration(exclude = {
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class,
            LiquibaseAutoConfiguration.class
    })
    @Import({SchemaInitializerConfig.class, BudgetPersistenceConfig.class, InvestmentPersistenceConfig.class})
    @ComponentScan(basePackages = {"pyc.lopatuxin.budget.service", "pyc.lopatuxin.budget.mapper"})
    static class TestApp {
    }
}
