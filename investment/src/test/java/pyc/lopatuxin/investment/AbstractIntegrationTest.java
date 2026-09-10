package pyc.lopatuxin.investment;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pyc.lopatuxin.investment.client.moex.MoexIssClient;
import pyc.lopatuxin.investment.repository.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    protected MockMvc mockMvc;

    // Replaces the real MOEX client for the whole integration test context so nothing here
    // ever hits the live exchange: neither ensureSecurity() calls made by tests, nor the
    // self-healing of PENDING securities that MarketDataService triggers on every context
    // startup (ApplicationReadyEvent). Subclasses that need specific responses stub this
    // field directly; unstubbed calls fall back to Mockito's empty defaults (empty
    // Optional/List/Map), which behave like "MOEX has no data for this ticker".
    @MockitoBean
    protected MoexIssClient moexIssClient;

    @BeforeAll
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    protected @Autowired SecurityRepository securityRepository;
    protected @Autowired TransactionRepository transactionRepository;
    protected @Autowired PositionRepository positionRepository;
    protected @Autowired PriceSnapshotRepository priceSnapshotRepository;
    protected @Autowired PriceHistoryRepository priceHistoryRepository;
    protected @Autowired DividendRepository dividendRepository;
}
