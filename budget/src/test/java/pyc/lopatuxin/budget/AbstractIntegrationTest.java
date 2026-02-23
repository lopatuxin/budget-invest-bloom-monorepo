package pyc.lopatuxin.budget;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pyc.lopatuxin.budget.repository.CapitalRecordRepository;
import pyc.lopatuxin.budget.repository.CategoryRepository;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

/**
 * Базовый класс для всех интеграционных тестов budget-сервиса.
 *
 * <p>Поднимает полный Spring-контекст с реальной PostgreSQL через Testcontainers.
 * Дочерние классы не должны повторно объявлять аннотации, заданные здесь.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    /** HTTP-клиент для тестирования контроллеров. */
    protected MockMvc mockMvc;

    @BeforeAll
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    /** Репозиторий категорий. */
    @Autowired
    protected CategoryRepository categoryRepository;

    /** Репозиторий расходов. */
    @Autowired
    protected ExpenseRepository expenseRepository;

    /** Репозиторий доходов. */
    @Autowired
    protected IncomeRepository incomeRepository;

    /** Репозиторий записей капитала. */
    @Autowired
    protected CapitalRecordRepository capitalRecordRepository;
}