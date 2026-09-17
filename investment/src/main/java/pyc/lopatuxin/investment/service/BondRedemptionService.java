package pyc.lopatuxin.investment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.investment.client.moex.MoexIssClient;
import pyc.lopatuxin.investment.client.moex.MoexUnavailableException;
import pyc.lopatuxin.investment.dto.response.MoexSecurityDto;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Closes a bond/OFZ position once its maturity date has arrived: records a {@code REDEMPTION}
 * transaction for the whole remaining quantity at face value, which recalculates the position
 * (closing it) and mirrors the payout into the budget (bond-redemption plan). Run nightly (see
 * {@code MarketDataRefreshScheduler}) and once right after application startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BondRedemptionService {

    private static final Set<SecurityType> REDEEMABLE_TYPES = EnumSet.of(SecurityType.BOND, SecurityType.OFZ);
    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
    private static final LocalTime REDEMPTION_TIME = LocalTime.NOON;

    private final PositionRepository positionRepository;
    private final SecurityRepository securityRepository;
    private final MoexIssClient moexIssClient;
    private final TransactionService transactionService;

    // Same background executor the nightly history/dividend jobs use, so application startup
    // does not block on MOEX round-trips (see MarketDataService.onApplicationReady).
    @Async("historyLoaderExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            redeemMatured();
        } catch (Exception e) {
            log.warn("Погашение облигаций на старте не выполнено: {}", e.getMessage());
        }
    }

    public void redeemMatured() {
        List<Position> positions = positionRepository.findBySecurity_TypeIn(REDEEMABLE_TYPES);
        if (positions.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now(MOSCOW_ZONE);
        // One MOEX lookup per ticker per run, even when many users hold the same bond or the
        // bond is perpetual and the lookup comes back empty.
        Map<String, Optional<LocalDate>> maturityByTicker = new HashMap<>();
        int redeemed = 0;
        for (Position position : positions) {
            Security security = position.getSecurity();
            LocalDate maturityDate = maturityByTicker
                    .computeIfAbsent(security.getTicker(), ticker -> resolveMaturityDate(security))
                    .orElse(null);
            if (maturityDate == null || maturityDate.isAfter(today)) {
                continue;
            }
            if (redeemPosition(position, maturityDate)) {
                redeemed++;
            }
        }
        if (redeemed > 0) {
            log.info("Погашение облигаций: закрыто позиций {}", redeemed);
        }
    }

    // Fills Security.maturityDate for a bond/OFZ created before this feature (new ones get it in
    // MarketDataService.ensureSecurity) — done lazily right before the redemption check, since
    // this job is the only place a still-null date actually matters. A perpetual bond (MOEX
    // returns no MATDATE at all) stays null forever and is simply never redeemed.
    private Optional<LocalDate> resolveMaturityDate(Security security) {
        if (security.getMaturityDate() != null) {
            return Optional.of(security.getMaturityDate());
        }
        Optional<LocalDate> fetched;
        try {
            fetched = moexIssClient.fetchSecurity(security.getTicker()).map(MoexSecurityDto::maturityDate);
        } catch (MoexUnavailableException e) {
            log.warn("MOEX недоступна, дата погашения {} не дозаполнена", security.getTicker());
            return Optional.empty();
        }
        fetched.ifPresent(date -> securityRepository.findById(security.getTicker()).ifPresent(stored -> {
            stored.setMaturityDate(date);
            securityRepository.save(stored);
        }));
        return fetched;
    }

    // Own transaction per position (TransactionService.createRedemption is @Transactional): a
    // failure on one bond must not roll back redemptions already recorded for others in the
    // same run.
    private boolean redeemPosition(Position position, LocalDate maturityDate) {
        Security security = position.getSecurity();
        String ticker = security.getTicker();
        if (security.getNominal() == null) {
            log.warn("Номинал не известен, погашение {} отложено", ticker);
            return false;
        }
        try {
            transactionService.createRedemption(position.getUserId(), security, position.getQuantity(),
                    security.getNominal(), maturityDate.atTime(REDEMPTION_TIME).atZone(MOSCOW_ZONE).toInstant());
            return true;
        } catch (Exception e) {
            log.warn("Погашение {} для userId={} не выполнено: {}", ticker, position.getUserId(), e.getMessage());
            return false;
        }
    }
}
