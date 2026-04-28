package pyc.lopatuxin.investment.service.market;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.client.moex.MoexIssClient;
import pyc.lopatuxin.investment.client.moex.MoexUnavailableException;
import pyc.lopatuxin.investment.client.moex.dto.MoexSecurityDto;
import pyc.lopatuxin.investment.client.moex.dto.MoexSnapshotDto;
import pyc.lopatuxin.investment.config.MoexProperties;
import pyc.lopatuxin.investment.entity.PriceSnapshot;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.PriceSnapshotRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;
import pyc.lopatuxin.investment.service.market.dto.SnapshotResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final MoexIssClient moexIssClient;
    private final SecurityRepository securityRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final MoexProperties moexProperties;

    @Transactional
    public Security ensureSecurity(String ticker, SecurityType fallbackType) {
        Optional<Security> existing = securityRepository.findById(ticker);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            Optional<MoexSecurityDto> moexDto = moexIssClient.fetchSecurity(ticker);
            Security security = moexDto
                    .map(dto -> buildReadySecurity(ticker, dto))
                    .orElseGet(() -> buildPendingSecurity(ticker, fallbackType));
            return securityRepository.save(security);
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for ticker {}, saving as PENDING", ticker);
            return securityRepository.save(buildPendingSecurity(ticker, fallbackType));
        }
    }

    @Cacheable(value = "moexSnapshots", key = "#ticker")
    @Transactional
    public SnapshotResult getSnapshot(String ticker) {
        Optional<PriceSnapshot> dbSnapshot = priceSnapshotRepository.findById(ticker);
        if (dbSnapshot.isPresent() && !isStale(dbSnapshot.get())) {
            return toSnapshotResult(dbSnapshot.get(), false);
        }
        try {
            Map<String, MoexSnapshotDto> fetched = moexIssClient.fetchSnapshots(List.of(ticker));
            MoexSnapshotDto dto = fetched.get(ticker);
            if (dto != null) {
                PriceSnapshot snapshot = upsertSnapshot(ticker, dto);
                return toSnapshotResult(snapshot, false);
            }
            return dbSnapshot.map(s -> toSnapshotResult(s, true))
                    .orElse(new SnapshotResult(null, null, null, true));
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for snapshot {}", ticker);
            return dbSnapshot.map(s -> toSnapshotResult(s, true))
                    .orElse(new SnapshotResult(null, null, null, true));
        }
    }

    @Transactional
    public Map<String, SnapshotResult> getSnapshots(Collection<String> tickers) {
        if (tickers.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, SnapshotResult> result = new HashMap<>();
        try {
            Map<String, MoexSnapshotDto> fetched = moexIssClient.fetchSnapshots(tickers);
            for (String ticker : tickers) {
                MoexSnapshotDto dto = fetched.get(ticker);
                if (dto != null) {
                    PriceSnapshot snapshot = upsertSnapshot(ticker, dto);
                    result.put(ticker, toSnapshotResult(snapshot, false));
                } else {
                    result.put(ticker, resolveFromDbOrStale(ticker));
                }
            }
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for batch snapshots");
            for (String ticker : tickers) {
                result.put(ticker, resolveFromDbOrStale(ticker));
            }
        }
        return result;
    }

    @Cacheable(value = "moexSecurities", key = "#query")
    public List<MoexSecurityDto> search(String query) {
        try {
            return moexIssClient.searchSecurities(query);
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for search query: {}", query);
            return Collections.emptyList();
        }
    }

    public MoexSecurityDto getSecurityInfo(String ticker) {
        Security security = ensureSecurity(ticker, SecurityType.STOCK);
        return new MoexSecurityDto(
                security.getTicker(),
                security.getBoardId(),
                security.getName(),
                security.getType(),
                security.getSector(),
                security.getCurrency()
        );
    }

    private Security buildReadySecurity(String ticker, MoexSecurityDto dto) {
        return Security.builder()
                .ticker(ticker)
                .boardId(dto.boardId())
                .name(dto.name() != null ? dto.name() : ticker)
                .type(dto.securityType())
                .sector(dto.sector())
                .currency(dto.currency())
                .historyStatus(HistoryStatus.READY)
                .build();
    }

    private Security buildPendingSecurity(String ticker, SecurityType fallbackType) {
        return Security.builder()
                .ticker(ticker)
                .name(ticker)
                .type(fallbackType)
                .historyStatus(HistoryStatus.PENDING)
                .build();
    }

    private PriceSnapshot upsertSnapshot(String ticker, MoexSnapshotDto dto) {
        PriceSnapshot snapshot = priceSnapshotRepository.findById(ticker)
                .orElseGet(() -> PriceSnapshot.builder().ticker(ticker).build());
        snapshot.setLastPrice(dto.lastPrice());
        snapshot.setPreviousClose(dto.previousClose());
        snapshot.setFetchedAt(Instant.now());
        return priceSnapshotRepository.save(snapshot);
    }

    private SnapshotResult resolveFromDbOrStale(String ticker) {
        return priceSnapshotRepository.findById(ticker)
                .map(s -> toSnapshotResult(s, true))
                .orElse(new SnapshotResult(null, null, null, true));
    }

    private boolean isStale(PriceSnapshot snapshot) {
        return snapshot.getFetchedAt().isBefore(
                Instant.now().minus(moexProperties.getSnapshotTtlMinutes(), ChronoUnit.MINUTES)
        );
    }

    private SnapshotResult toSnapshotResult(PriceSnapshot snapshot, boolean stale) {
        return new SnapshotResult(snapshot.getLastPrice(), snapshot.getPreviousClose(), snapshot.getFetchedAt(), stale);
    }
}
