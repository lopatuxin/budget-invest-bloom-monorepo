package pyc.lopatuxin.investment.service.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.client.moex.MoexIssClient;
import pyc.lopatuxin.investment.client.moex.MoexUnavailableException;
import pyc.lopatuxin.investment.client.moex.dto.MoexCandleDto;
import pyc.lopatuxin.investment.client.moex.dto.MoexSecurityDto;
import pyc.lopatuxin.investment.client.moex.dto.MoexSnapshotDto;
import pyc.lopatuxin.investment.config.MoexProperties;
import pyc.lopatuxin.investment.dto.request.SearchCategory;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.PriceSnapshot;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.PriceHistoryRepository;
import pyc.lopatuxin.investment.repository.PriceSnapshotRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;
import pyc.lopatuxin.investment.service.market.dto.SnapshotResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class MarketDataService {

    private final MoexIssClient moexIssClient;
    private final SecurityRepository securityRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MoexProperties moexProperties;
    private final HistoryLoaderService historyLoaderService;
    private final MarketDataService self;

    public MarketDataService(MoexIssClient moexIssClient,
                             SecurityRepository securityRepository,
                             PriceSnapshotRepository priceSnapshotRepository,
                             PriceHistoryRepository priceHistoryRepository,
                             MoexProperties moexProperties,
                             @Lazy HistoryLoaderService historyLoaderService,
                             @Lazy MarketDataService self) {
        this.moexIssClient = moexIssClient;
        this.securityRepository = securityRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.moexProperties = moexProperties;
        this.historyLoaderService = historyLoaderService;
        this.self = self;
    }

    public Security ensureSecurity(String ticker, SecurityType fallbackType) {
        String normalizedTicker = ticker.toUpperCase();
        Optional<Security> existing = securityRepository.findById(normalizedTicker);
        if (existing.isPresent()) {
            return existing.get();
        }
        MoexSecurityDto moexDto = null;
        try {
            moexDto = moexIssClient.fetchSecurity(normalizedTicker).orElse(null);
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for ticker {}, saving as PENDING", normalizedTicker);
        }
        return self.persistNewSecurity(normalizedTicker, moexDto, fallbackType);
    }

    @Transactional
    public Security persistNewSecurity(String ticker, MoexSecurityDto moexDto, SecurityType fallbackType) {
        return securityRepository.findById(ticker).orElseGet(() -> {
            Security security = moexDto != null
                    ? buildReadySecurity(ticker, moexDto)
                    : buildPendingSecurity(ticker, fallbackType);
            return securityRepository.save(security);
        });
    }

    public SnapshotResult getSnapshotReadOnly(String ticker) {
        Optional<PriceSnapshot> dbSnapshot = priceSnapshotRepository.findById(ticker);
        if (dbSnapshot.isPresent() && !isStale(dbSnapshot.get())) {
            return toSnapshotResult(dbSnapshot.get(), false);
        }
        try {
            Map<String, MoexSnapshotDto> fetched = moexIssClient.fetchSnapshots(List.of(ticker));
            MoexSnapshotDto dto = fetched.get(ticker);
            if (dto != null) {
                return new SnapshotResult(dto.lastPrice(), dto.previousClose(), Instant.now(), false);
            }
            return dbSnapshot.map(s -> toSnapshotResult(s, true))
                    .orElse(new SnapshotResult(null, null, null, true));
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for snapshot {} (read-only)", ticker);
            return dbSnapshot.map(s -> toSnapshotResult(s, true))
                    .orElse(new SnapshotResult(null, null, null, true));
        }
    }

    @Cacheable(value = "moexSnapshots", key = "#ticker")
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

    @Cacheable(value = "moexSecurities", key = "'list:' + (#category != null ? #category.name() : 'ALL')")
    public List<MoexSecurityDto> listSecurities(SearchCategory category) {
        try {
            List<MoexSecurityDto> result = new ArrayList<>();
            if (category == null || category == SearchCategory.STOCKS) {
                result.addAll(moexIssClient.listBoardSecurities("shares", "TQBR", SecurityType.STOCK));
                result.addAll(moexIssClient.listBoardSecurities("shares", "TQTF", SecurityType.ETF));
            }
            if (category == null || category == SearchCategory.BONDS) {
                result.addAll(moexIssClient.listBoardSecurities("bonds", "TQOB", SecurityType.BOND));
            }
            return result;
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for listSecurities category={}", category);
            return Collections.emptyList();
        }
    }

    @Cacheable(value = "moexSecurities", key = "#query + ':' + (#category != null ? #category.name() : 'ALL')")
    public List<MoexSecurityDto> search(String query, SearchCategory category) {
        try {
            List<MoexSecurityDto> results = moexIssClient.searchSecurities(query);
            if (category == null) {
                return results;
            }
            Set<SecurityType> allowed = resolveTypes(category);
            return results.stream()
                    .filter(r -> allowed.contains(r.securityType()))
                    .toList();
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for search query: {}", query);
            return Collections.emptyList();
        }
    }

    private Set<SecurityType> resolveTypes(SearchCategory category) {
        return switch (category) {
            case STOCKS -> Set.of(SecurityType.STOCK, SecurityType.ETF);
            case BONDS -> Set.of(SecurityType.BOND, SecurityType.OFZ);
        };
    }

    public void ensureHistory(String ticker) {
        Optional<Security> secOpt = securityRepository.findById(ticker);
        if (secOpt.isEmpty()) {
            log.warn("Security {} not found, skipping history load", ticker);
            return;
        }
        Security security = secOpt.get();
        if (security.getHistoryStatus() == HistoryStatus.READY && priceHistoryRepository.existsByTicker(ticker)) {
            return;
        }
        LocalDate from = LocalDate.now().minusYears(3);
        LocalDate to = LocalDate.now();
        try {
            List<MoexCandleDto> candles = moexIssClient.fetchHistory(ticker, from, to);
            List<PriceHistory> records = candles.stream()
                    .map(c -> PriceHistory.builder()
                            .ticker(c.ticker())
                            .tradeDate(c.tradeDate())
                            .open(c.open())
                            .close(c.close())
                            .high(c.high())
                            .low(c.low())
                            .volume(c.volume())
                            .build())
                    .toList();
            self.saveHistoryAndUpdateStatus(ticker, records);
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable for history {}, status remains PENDING", ticker);
        }
    }

    @Transactional
    public void saveHistoryAndUpdateStatus(String ticker, List<PriceHistory> records) {
        priceHistoryRepository.saveAll(records);
        securityRepository.findById(ticker).ifPresent(s -> {
            s.setHistoryStatus(HistoryStatus.READY);
            securityRepository.save(s);
        });
    }

    public void triggerHistoryAsync(String ticker) {
        historyLoaderService.loadAsync(ticker);
    }

    public HistoryStatus getSecurityHistoryStatus(String ticker) {
        return securityRepository.findById(ticker)
                .map(Security::getHistoryStatus)
                .orElse(HistoryStatus.PENDING);
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
