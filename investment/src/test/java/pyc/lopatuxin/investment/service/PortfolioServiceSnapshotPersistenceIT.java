package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pyc.lopatuxin.investment.AbstractIntegrationTest;
import pyc.lopatuxin.investment.dto.request.PortfolioSort;
import pyc.lopatuxin.investment.dto.response.MoexSnapshotDto;
import pyc.lopatuxin.investment.dto.response.PortfolioPageResponseDto;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.PriceSnapshot;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Reproduces the bug: getPortfolioPage runs inside a readOnly transaction, and a snapshot
 * upsert nested inside it (via MarketDataService.getSnapshots) used to join that same
 * transaction, which Hibernate puts in manual flush mode — the save() was never actually
 * flushed to the database. Checks the fix (a REQUIRES_NEW transaction on the upsert) by
 * reading price_snapshots back through a completely fresh repository call after the page
 * request returns.
 */
@DisplayName("PortfolioServiceSnapshotPersistenceIT — снимок цены реально коммитится при сборке страницы")
class PortfolioServiceSnapshotPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private PortfolioService portfolioService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        dividendRepository.deleteAll();
        priceSnapshotRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        transactionRepository.deleteAll();
        positionRepository.deleteAll();
        securityRepository.deleteAll();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getPortfolioPage — снимок с биржи сохраняется в price_snapshots, а не только в ответе")
    void getPortfolioPage_snapshotFromMoex_isPersistedToDatabase() {
        securityRepository.save(Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .sector("Финансы")
                .historyStatus(HistoryStatus.READY)
                .build());
        positionRepository.save(Position.builder()
                .userId(userId)
                .security(securityRepository.findById("SBER").orElseThrow())
                .quantity(new BigDecimal("10.00000000"))
                .averagePrice(new BigDecimal("280.00"))
                .totalCost(new BigDecimal("2800.00"))
                .build());
        when(moexIssClient.fetchSnapshots(any()))
                .thenReturn(Map.of("SBER", new MoexSnapshotDto("SBER", new BigDecimal("310.50"), new BigDecimal("308.00"))));

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getPositions()).hasSize(1);
        assertThat(page.getPositions().get(0).getCurrentPrice()).isEqualByComparingTo("310.50");

        Optional<PriceSnapshot> persisted = priceSnapshotRepository.findById("SBER");
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getLastPrice()).isEqualByComparingTo("310.50");
        assertThat(persisted.get().getPreviousClose()).isEqualByComparingTo("308.00");
    }

    @Test
    @DisplayName("getPortfolioPage — MOEX вернул LAST=null (нет торгов), PREVPRICE заполнен, снимка ещё нет → страница не падает, снимок сохраняется только с ценой закрытия")
    void getPortfolioPage_moexReturnsNullLastPrice_noExistingSnapshot_persistsPreviousCloseOnly() {
        securityRepository.save(Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .sector("Финансы")
                .historyStatus(HistoryStatus.READY)
                .build());
        positionRepository.save(Position.builder()
                .userId(userId)
                .security(securityRepository.findById("SBER").orElseThrow())
                .quantity(new BigDecimal("10.00000000"))
                .averagePrice(new BigDecimal("280.00"))
                .totalCost(new BigDecimal("2800.00"))
                .build());
        // Real shape outside trading hours / on weekends: LAST absent, PREVPRICE present — used
        // to violate the last_price NOT NULL constraint and turn into a 500; now last_price is
        // nullable and, with no existing snapshot to protect, the closing price is worth saving.
        when(moexIssClient.fetchSnapshots(any()))
                .thenReturn(Map.of("SBER", new MoexSnapshotDto("SBER", null, new BigDecimal("308.00"))));

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getPositions()).hasSize(1);
        assertThat(page.getPositions().get(0).getCurrentPrice()).isNull();
        assertThat(page.getOverview().getUnpricedCount()).isEqualTo(1);

        Optional<PriceSnapshot> persisted = priceSnapshotRepository.findById("SBER");
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getLastPrice()).isNull();
        assertThat(persisted.get().getPreviousClose()).isEqualByComparingTo("308.00");
    }

    @Test
    @DisplayName("getPortfolioPage — MOEX вернул LAST=null (нет торгов), снимок уже есть → страница не падает, цена закрытия обновляется, текущая цена не затирается")
    void getPortfolioPage_moexReturnsNullLastPrice_existingSnapshot_previousCloseRefreshedLastPricePreserved() {
        securityRepository.save(Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .sector("Финансы")
                .historyStatus(HistoryStatus.READY)
                .build());
        positionRepository.save(Position.builder()
                .userId(userId)
                .security(securityRepository.findById("SBER").orElseThrow())
                .quantity(new BigDecimal("10.00000000"))
                .averagePrice(new BigDecimal("280.00"))
                .totalCost(new BigDecimal("2800.00"))
                .build());
        priceSnapshotRepository.save(PriceSnapshot.builder()
                .ticker("SBER")
                .lastPrice(new BigDecimal("300.00"))
                .previousClose(new BigDecimal("298.00"))
                .fetchedAt(java.time.Instant.now().minusSeconds(600))
                .build());
        when(moexIssClient.fetchSnapshots(any()))
                .thenReturn(Map.of("SBER", new MoexSnapshotDto("SBER", null, new BigDecimal("308.00"))));

        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(userId, PortfolioSort.WEIGHT);

        assertThat(page.getPositions()).hasSize(1);

        // MOEX did answer (just with no new trade) — previousClose/fetchedAt are refreshed so
        // the row is not a permanently stale badge on the page, while the last known real trade
        // price is preserved untouched.
        Optional<PriceSnapshot> persisted = priceSnapshotRepository.findById("SBER");
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getLastPrice()).isEqualByComparingTo("300.00");
        assertThat(persisted.get().getPreviousClose()).isEqualByComparingTo("308.00");
    }
}
