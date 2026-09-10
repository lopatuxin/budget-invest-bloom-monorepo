package pyc.lopatuxin.investment.service.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.client.tinvest.TinvestApi;
import pyc.lopatuxin.investment.client.tinvest.TinvestFindInstrumentRequest;
import pyc.lopatuxin.investment.client.tinvest.TinvestFindInstrumentResponse;
import pyc.lopatuxin.investment.client.tinvest.TinvestInstrumentShort;
import pyc.lopatuxin.investment.client.tinvest.TinvestResilience;
import pyc.lopatuxin.investment.client.tinvest.TinvestUnauthorizedException;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.SecurityRepository;

import java.util.List;
import java.util.Map;

/**
 * Resolves a {@link Security} to its T-Invest instrument (uid + classCode) by ticker, once —
 * the result is cached on the entity, so a security already resolved is never looked up again
 * (see plan point 10).
 */
@Slf4j
@Component
public class TinvestInstrumentResolver {

    private static final Map<SecurityType, List<String>> PREFERRED_CLASS_CODES = Map.of(
            SecurityType.STOCK, List.of("TQBR"),
            SecurityType.BOND, List.of("TQOB", "TQCB"),
            SecurityType.OFZ, List.of("TQOB", "TQCB"),
            SecurityType.ETF, List.of("TQTF")
    );

    private static final Map<SecurityType, String> INSTRUMENT_KIND = Map.of(
            SecurityType.STOCK, "INSTRUMENT_TYPE_SHARE",
            SecurityType.BOND, "INSTRUMENT_TYPE_BOND",
            SecurityType.OFZ, "INSTRUMENT_TYPE_BOND",
            SecurityType.ETF, "INSTRUMENT_TYPE_ETF"
    );

    private final TinvestApi tinvestApi;
    private final TinvestResilience tinvestResilience;
    private final SecurityRepository securityRepository;
    private final TinvestInstrumentResolver self;

    public TinvestInstrumentResolver(TinvestApi tinvestApi,
                                     TinvestResilience tinvestResilience,
                                     SecurityRepository securityRepository,
                                     @Lazy TinvestInstrumentResolver self) {
        this.tinvestApi = tinvestApi;
        this.tinvestResilience = tinvestResilience;
        this.securityRepository = securityRepository;
        this.self = self;
    }

    /**
     * @return true if the security now has a tinvestUid (already had one, or one was just found)
     * @throws TinvestUnauthorizedException propagated as-is; the current caller (DividendSyncService)
     * catches it per ticker and continues the batch — relying on TinvestResilience to trip its
     * circuit breaker after the first 401 so the rest of the batch fails fast instead of retrying
     */
    // Not @Transactional: findInstrument below is a network call to T-Invest (through
    // tinvestResilience — up to 3 retries and a 429 pause of up to 60s) — a transaction spanning
    // it would hold a connection from the 5-connection pool for that whole time. Only the DB
    // write at the end (self.saveResolution) runs inside its own short transaction, reached
    // through the self proxy so @Transactional actually applies (plain self-invocation bypasses
    // the Spring AOP proxy).
    public boolean resolve(Security security) {
        if (security.getTinvestUid() != null) {
            return true;
        }
        String ticker = security.getTicker();
        String kind = INSTRUMENT_KIND.get(security.getType());
        TinvestFindInstrumentResponse response = tinvestResilience.execute("findInstrument",
                () -> tinvestApi.findInstrument(new TinvestFindInstrumentRequest(ticker, kind)));

        List<TinvestInstrumentShort> matches = response.instruments() == null ? List.of() :
                response.instruments().stream()
                        .filter(i -> ticker.equalsIgnoreCase(i.ticker()))
                        .toList();
        if (matches.isEmpty()) {
            log.warn("Бумага {} не найдена в T-Invest", ticker);
            return false;
        }

        TinvestInstrumentShort chosen = choosePreferred(matches, security.getType());
        security.setTinvestUid(chosen.uid());
        security.setTinvestClassCode(chosen.classCode());
        self.saveResolution(ticker, chosen.uid(), chosen.classCode());
        return true;
    }

    // Re-reads the Security by id inside this write transaction instead of merging the instance
    // the caller resolved against: that instance sat detached during the network round-trip to
    // T-Invest, and saving it here would overwrite whatever another transaction wrote to the same
    // row (historyStatus, lastPriceUpdatedAt) in the meantime with a stale snapshot — a lost
    // update. Only tinvestUid/tinvestClassCode are changed on the freshly loaded entity.
    @Transactional("investmentTransactionManager")
    public void saveResolution(String ticker, String uid, String classCode) {
        Security security = securityRepository.findById(ticker).orElse(null);
        if (security == null) {
            return;
        }
        security.setTinvestUid(uid);
        security.setTinvestClassCode(classCode);
        securityRepository.save(security);
    }

    private TinvestInstrumentShort choosePreferred(List<TinvestInstrumentShort> matches, SecurityType type) {
        for (String preferredCode : PREFERRED_CLASS_CODES.getOrDefault(type, List.of())) {
            for (TinvestInstrumentShort candidate : matches) {
                if (preferredCode.equals(candidate.classCode())) {
                    return candidate;
                }
            }
        }
        return matches.get(0);
    }
}
