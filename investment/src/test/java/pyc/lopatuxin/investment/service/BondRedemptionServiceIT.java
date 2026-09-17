package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pyc.lopatuxin.investment.AbstractIntegrationTest;
import pyc.lopatuxin.investment.dto.response.MoexSecurityDto;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
import pyc.lopatuxin.shared.port.EntryType;
import pyc.lopatuxin.shared.port.InvestmentBudgetSync;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// No @Transactional on any test method here: BondRedemptionService.redeemMatured() commits each
// position in its own transaction, so an outer test transaction that never commits would hide
// exactly the bugs this feature is about (see the project's transactional-tests-hide-bugs note).
@DisplayName("BondRedemptionServiceIT — погашение облигаций по дате погашения")
class BondRedemptionServiceIT extends AbstractIntegrationTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Autowired
    private BondRedemptionService bondRedemptionService;

    // Overrides TestApplication's no-op stub with a mock so budgetEntryId/EntryType can be
    // asserted per test (and, in the per-position-transaction test, made to fail for one user).
    @MockitoBean
    private InvestmentBudgetSync investmentBudgetSync;

    private UUID userId;

    @BeforeEach
    void cleanUp() {
        dividendRepository.deleteAll();
        priceSnapshotRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        transactionRepository.deleteAll();
        positionRepository.deleteAll();
        securityRepository.deleteAll();
        userId = UUID.randomUUID();
        when(investmentBudgetSync.createEntry(any(), any(), any(), any())).thenReturn(UUID.randomUUID());
    }

    @Test
    @DisplayName("дата погашения наступила — позиция закрыта, записана REDEMPTION по номиналу датой погашения, доход ушёл в бюджет")
    void redeemMatured_maturedPosition_closesPositionAndRecordsRedemption() {
        LocalDate maturityDate = LocalDate.now(MOSCOW).minusDays(1);
        Security bond = saveBond("SU26219RMFS4", maturityDate, "1000.00");
        buyAndOpenPosition(userId, bond, "71", "998.00", maturityDate.minusMonths(6));

        bondRedemptionService.redeemMatured();

        assertThat(positionRepository.findByUserIdAndSecurity_Ticker(userId, "SU26219RMFS4")).isEmpty();

        List<Transaction> journal = transactionRepository.findByUserIdAndSecurity_Ticker(userId, "SU26219RMFS4");
        Transaction redemption = journal.stream().filter(t -> t.getType() == TransactionType.REDEMPTION).findFirst().orElseThrow();
        assertThat(redemption.getQuantity()).isEqualByComparingTo("71");
        assertThat(redemption.getPrice()).isEqualByComparingTo("1000.00");
        assertThat(redemption.getExecutedAt()).isEqualTo(maturityDate.atTime(LocalTime.NOON).atZone(MOSCOW).toInstant());
        assertThat(redemption.getBudgetEntryId()).isNotNull();

        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(investmentBudgetSync).createEntry(eq(userId), eq(EntryType.REDEMPTION), amountCaptor.capture(), any(Instant.class));
        assertThat(amountCaptor.getValue()).isEqualByComparingTo("71000.00");
    }

    @Test
    @DisplayName("дата погашения в будущем — позиция не трогается")
    void redeemMatured_futureMaturityDate_leavesPositionUntouched() {
        LocalDate maturityDate = LocalDate.now(MOSCOW).plusYears(1);
        Security bond = saveBond("SU26238RMFS4", maturityDate, "1000.00");
        buyAndOpenPosition(userId, bond, "10", "990.00", LocalDate.now(MOSCOW).minusMonths(1));

        bondRedemptionService.redeemMatured();

        assertThat(positionRepository.findByUserIdAndSecurity_Ticker(userId, "SU26238RMFS4")).isPresent();
        assertThat(transactionRepository.findByUserIdAndSecurity_Ticker(userId, "SU26238RMFS4"))
                .noneMatch(t -> t.getType() == TransactionType.REDEMPTION);
    }

    @Test
    @DisplayName("дата погашения пустая, MOEX тоже не отдаёт MATDATE (бессрочная) — позиция не трогается")
    void redeemMatured_noMaturityDateAnywhere_leavesPositionUntouched() {
        Security bond = saveBond("PERPBOND", null, "1000.00");
        buyAndOpenPosition(userId, bond, "5", "1000.00", LocalDate.now(MOSCOW).minusMonths(1));
        when(moexIssClient.fetchSecurity("PERPBOND")).thenReturn(Optional.of(
                new MoexSecurityDto("PERPBOND", "TQOB", "Бессрочная", SecurityType.BOND, "Облигации", "RUB", null)));

        bondRedemptionService.redeemMatured();

        assertThat(positionRepository.findByUserIdAndSecurity_Ticker(userId, "PERPBOND")).isPresent();
        assertThat(securityRepository.findById("PERPBOND").orElseThrow().getMaturityDate()).isNull();
    }

    @Test
    @DisplayName("дата погашения пустая в базе, дозаполняется с биржи — позиция с наступившей датой погашается")
    void redeemMatured_backfillsMaturityDateFromMoex_thenRedeemsIfMatured() {
        LocalDate maturityDate = LocalDate.now(MOSCOW).minusDays(2);
        Security bond = saveBond("SU26219RMFS4", null, "1000.00");
        buyAndOpenPosition(userId, bond, "20", "995.00", maturityDate.minusMonths(3));
        when(moexIssClient.fetchSecurity("SU26219RMFS4")).thenReturn(Optional.of(
                new MoexSecurityDto("SU26219RMFS4", "TQOB", "ОФЗ 26219", SecurityType.OFZ, "Государственные облигации", "RUB", maturityDate)));

        bondRedemptionService.redeemMatured();

        assertThat(securityRepository.findById("SU26219RMFS4").orElseThrow().getMaturityDate()).isEqualTo(maturityDate);
        assertThat(positionRepository.findByUserIdAndSecurity_Ticker(userId, "SU26219RMFS4")).isEmpty();
    }

    @Test
    @DisplayName("номинал не известен — позиция не трогается")
    void redeemMatured_nullNominal_leavesPositionUntouched() {
        Security bond = saveBond("NONOMINAL", LocalDate.now(MOSCOW).minusDays(1), null);
        buyAndOpenPosition(userId, bond, "3", "500.00", LocalDate.now(MOSCOW).minusMonths(1));

        bondRedemptionService.redeemMatured();

        assertThat(positionRepository.findByUserIdAndSecurity_Ticker(userId, "NONOMINAL")).isPresent();
        assertThat(transactionRepository.findByUserIdAndSecurity_Ticker(userId, "NONOMINAL"))
                .noneMatch(t -> t.getType() == TransactionType.REDEMPTION);
    }

    @Test
    @DisplayName("повторный запуск не создаёт второе погашение (позиция уже закрыта)")
    void redeemMatured_secondRun_doesNotDuplicate() {
        LocalDate maturityDate = LocalDate.now(MOSCOW).minusDays(1);
        Security bond = saveBond("SU26219RMFS4", maturityDate, "1000.00");
        buyAndOpenPosition(userId, bond, "71", "998.00", maturityDate.minusMonths(6));

        bondRedemptionService.redeemMatured();
        bondRedemptionService.redeemMatured();

        List<Transaction> redemptions = transactionRepository.findByUserIdAndSecurity_Ticker(userId, "SU26219RMFS4").stream()
                .filter(t -> t.getType() == TransactionType.REDEMPTION)
                .toList();
        assertThat(redemptions).hasSize(1);
    }

    @Test
    @DisplayName("ошибка погашения одного пользователя не откатывает погашение другого (своя транзакция на позицию)")
    void redeemMatured_onePositionFails_othersStillRedeemed() {
        LocalDate maturityDate = LocalDate.now(MOSCOW).minusDays(1);
        Security bond = saveBond("SU26219RMFS4", maturityDate, "1000.00");
        UUID failingUserId = UUID.randomUUID();
        buyAndOpenPosition(failingUserId, bond, "10", "998.00", maturityDate.minusMonths(6));
        buyAndOpenPosition(userId, bond, "71", "998.00", maturityDate.minusMonths(6));

        doThrow(new RuntimeException("budget unavailable"))
                .when(investmentBudgetSync).createEntry(eq(failingUserId), any(), any(), any());

        bondRedemptionService.redeemMatured();

        assertThat(positionRepository.findByUserIdAndSecurity_Ticker(failingUserId, "SU26219RMFS4")).isPresent();
        assertThat(positionRepository.findByUserIdAndSecurity_Ticker(userId, "SU26219RMFS4")).isEmpty();
    }

    private Security saveBond(String ticker, LocalDate maturityDate, String nominal) {
        return securityRepository.save(Security.builder()
                .ticker(ticker)
                .name(ticker)
                .type(SecurityType.OFZ)
                .historyStatus(HistoryStatus.READY)
                .nominal(nominal != null ? new BigDecimal(nominal) : null)
                .maturityDate(maturityDate)
                .build());
    }

    // Persists a BUY transaction and the resulting open position directly (bypassing
    // TransactionService) — this test is about BondRedemptionService, not the BUY recalculation
    // TransactionServiceUnitTest already covers.
    private void buyAndOpenPosition(UUID owner, Security security, String quantity, String price, LocalDate executedAtDate) {
        BigDecimal qty = new BigDecimal(quantity);
        BigDecimal buyPrice = new BigDecimal(price);
        transactionRepository.save(Transaction.builder()
                .userId(owner)
                .security(security)
                .type(TransactionType.BUY)
                .quantity(qty)
                .price(buyPrice)
                .executedAt(executedAtDate.atTime(LocalTime.NOON).atZone(MOSCOW).toInstant())
                .build());
        positionRepository.save(Position.builder()
                .userId(owner)
                .security(security)
                .quantity(qty)
                .averagePrice(buyPrice)
                .totalCost(qty.multiply(buyPrice).setScale(2, java.math.RoundingMode.HALF_UP))
                .build());
    }
}
