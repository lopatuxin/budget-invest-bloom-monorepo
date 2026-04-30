package pyc.lopatuxin.investment.client.moex;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import pyc.lopatuxin.investment.client.moex.dto.MoexCandleDto;
import pyc.lopatuxin.investment.client.moex.dto.MoexDividendDto;
import pyc.lopatuxin.investment.client.moex.dto.MoexSecurityDto;
import pyc.lopatuxin.investment.client.moex.dto.MoexSnapshotDto;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MoexIssClient {

    private final RestClient moexRestClient;
    private final ObjectMapper objectMapper;

    @Retry(name = "moex", fallbackMethod = "fetchSecurityFallback")
    @CircuitBreaker(name = "moex", fallbackMethod = "fetchSecurityFallback")
    public Optional<MoexSecurityDto> fetchSecurity(String ticker) {
        String json = moexRestClient.get()
                .uri("/securities/{ticker}.json?iss.only=description,securities&iss.meta=off", ticker)
                .retrieve()
                .body(String.class);
        return parseSecurity(json, ticker);
    }

    @SuppressWarnings("unused")
    public Optional<MoexSecurityDto> fetchSecurityFallback(String ticker, Throwable t) {
        log.warn("MOEX fetchSecurity fallback for {}: {}", ticker, t.getMessage());
        throw new MoexUnavailableException("MOEX unavailable: " + t.getMessage());
    }

    @Retry(name = "moex", fallbackMethod = "fetchSnapshotsFallback")
    @CircuitBreaker(name = "moex", fallbackMethod = "fetchSnapshotsFallback")
    public Map<String, MoexSnapshotDto> fetchSnapshots(Collection<String> tickers) {
        if (tickers.isEmpty()) {
            return Collections.emptyMap();
        }
        String csv = String.join(",", tickers);
        Map<String, MoexSnapshotDto> result = new HashMap<>();
        result.putAll(fetchMarketData(csv, "stock", "shares"));
        result.putAll(fetchMarketData(csv, "stock", "bonds"));
        return result;
    }

    @SuppressWarnings("unused")
    public Map<String, MoexSnapshotDto> fetchSnapshotsFallback(Collection<String> tickers, Throwable t) {
        log.warn("MOEX fetchSnapshots fallback: {}", t.getMessage());
        throw new MoexUnavailableException("MOEX unavailable: " + t.getMessage());
    }

    @Retry(name = "moex", fallbackMethod = "searchSecuritiesFallback")
    @CircuitBreaker(name = "moex", fallbackMethod = "searchSecuritiesFallback")
    public List<MoexSecurityDto> searchSecurities(String query) {
        String json = moexRestClient.get()
                .uri("/securities.json?q={q}&limit=20&iss.only=securities&iss.meta=off", query)
                .retrieve()
                .body(String.class);
        return parseSearchResults(json);
    }

    @SuppressWarnings("unused")
    public List<MoexSecurityDto> searchSecuritiesFallback(String query, Throwable t) {
        log.warn("MOEX searchSecurities fallback for {}: {}", query, t.getMessage());
        throw new MoexUnavailableException("MOEX unavailable: " + t.getMessage());
    }

    @Retry(name = "moex", fallbackMethod = "fetchHistoryFallback")
    @CircuitBreaker(name = "moex", fallbackMethod = "fetchHistoryFallback")
    public List<MoexCandleDto> fetchHistory(String ticker, LocalDate from, LocalDate to) {
        List<MoexCandleDto> result = fetchHistoryFromMarket(ticker, "shares", from, to);
        if (result.isEmpty()) {
            result = fetchHistoryFromMarket(ticker, "bonds", from, to);
        }
        return result;
    }

    @SuppressWarnings("unused")
    public List<MoexCandleDto> fetchHistoryFallback(String ticker, LocalDate from, LocalDate to, Throwable t) {
        log.warn("MOEX fetchHistory fallback for {}: {}", ticker, t.getMessage());
        throw new MoexUnavailableException("MOEX unavailable: " + t.getMessage());
    }

    @Retry(name = "moex", fallbackMethod = "fetchDividendsFallback")
    @CircuitBreaker(name = "moex", fallbackMethod = "fetchDividendsFallback")
    public List<MoexDividendDto> fetchDividends(String ticker) {
        String json = moexRestClient.get()
                .uri("/securities/{ticker}/dividends.json?iss.meta=off&iss.only=dividends", ticker)
                .retrieve()
                .body(String.class);
        return parseDividends(json);
    }

    @SuppressWarnings("unused")
    public List<MoexDividendDto> fetchDividendsFallback(String ticker, Throwable t) {
        log.warn("MOEX fetchDividends fallback for {}: {}", ticker, t.getMessage());
        throw new MoexUnavailableException("MOEX unavailable: " + t.getMessage());
    }

    private List<MoexCandleDto> fetchHistoryFromMarket(String ticker, String market, LocalDate from, LocalDate to) {
        List<MoexCandleDto> all = new ArrayList<>();
        int start = 0;
        while (true) {
            String json = fetchHistoryPageJson(ticker, market, from, to, start);
            if (json == null) break;
            List<MoexCandleDto> page = parseHistoryPage(json, ticker);
            all.addAll(page);
            if (page.isEmpty()) break;
            int[] cursor = parseHistoryCursor(json);
            if (cursor == null || cursor[0] + cursor[2] >= cursor[1]) break;
            start = cursor[0] + cursor[2];
        }
        return all;
    }

    private String fetchHistoryPageJson(String ticker, String market, LocalDate from, LocalDate to, int start) {
        return moexRestClient.get()
                .uri("/history/engines/stock/markets/{market}/securities/{ticker}.json?from={from}&till={to}&iss.meta=off&iss.only=history,history.cursor&start={start}",
                        market, ticker, from, to, start)
                .retrieve()
                .body(String.class);
    }

    @SuppressWarnings("unchecked")
    private List<MoexCandleDto> parseHistoryPage(String json, String ticker) {
        if (json == null) return Collections.emptyList();
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> historyBlock = (Map<String, Object>) root.get("history");
            if (historyBlock == null) return Collections.emptyList();

            List<String> cols = (List<String>) historyBlock.get("columns");
            List<List<Object>> data = (List<List<Object>>) historyBlock.get("data");
            if (cols == null || data == null || data.isEmpty()) return Collections.emptyList();

            int dateIdx = MoexJsonMapper.indexOf(cols, "TRADEDATE");
            int openIdx = MoexJsonMapper.indexOf(cols, "OPEN");
            int closeIdx = MoexJsonMapper.indexOf(cols, "CLOSE");
            int highIdx = MoexJsonMapper.indexOf(cols, "HIGH");
            int lowIdx = MoexJsonMapper.indexOf(cols, "LOW");
            int volumeIdx = MoexJsonMapper.indexOf(cols, "VOLUME");

            List<MoexCandleDto> result = new ArrayList<>();
            for (List<Object> row : data) {
                BigDecimal close = MoexJsonMapper.decimal(row, closeIdx);
                if (close == null || close.compareTo(BigDecimal.ZERO) == 0) continue;
                String dateStr = MoexJsonMapper.str(row, dateIdx);
                if (dateStr == null) continue;
                LocalDate tradeDate = LocalDate.parse(dateStr);
                BigDecimal open = MoexJsonMapper.decimal(row, openIdx);
                BigDecimal high = MoexJsonMapper.decimal(row, highIdx);
                BigDecimal low = MoexJsonMapper.decimal(row, lowIdx);
                Long volume = parseVolume(row, volumeIdx);
                result.add(new MoexCandleDto(ticker, tradeDate, open, close, high, low, volume));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse MOEX history page for {}: {}", ticker, e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private int[] parseHistoryCursor(String json) {
        if (json == null) return null;
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> cursorBlock = (Map<String, Object>) root.get("history.cursor");
            if (cursorBlock == null) return null;

            List<String> cols = (List<String>) cursorBlock.get("columns");
            List<List<Object>> data = (List<List<Object>>) cursorBlock.get("data");
            if (cols == null || data == null || data.isEmpty()) return null;

            List<Object> row = data.get(0);
            int indexIdx = MoexJsonMapper.indexOf(cols, "INDEX");
            int totalIdx = MoexJsonMapper.indexOf(cols, "TOTAL");
            int pageSizeIdx = MoexJsonMapper.indexOf(cols, "PAGESIZE");

            BigDecimal index = MoexJsonMapper.decimal(row, indexIdx);
            BigDecimal total = MoexJsonMapper.decimal(row, totalIdx);
            BigDecimal pageSize = MoexJsonMapper.decimal(row, pageSizeIdx);
            if (index == null || total == null || pageSize == null) return null;

            return new int[]{index.intValue(), total.intValue(), pageSize.intValue()};
        } catch (Exception e) {
            log.error("Failed to parse MOEX history cursor: {}", e.getMessage());
            return null;
        }
    }

    private Long parseVolume(List<Object> row, int idx) {
        if (idx < 0 || idx >= row.size()) return null;
        Object val = row.get(idx);
        if (val == null) return null;
        try {
            return new BigDecimal(val.toString()).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, MoexSnapshotDto> fetchMarketData(String csv, String engine, String market) {
        try {
            String json = moexRestClient.get()
                    .uri("/engines/{engine}/markets/{market}/securities.json?securities={csv}&iss.only=marketdata,securities&iss.meta=off",
                            engine, market, csv)
                    .retrieve()
                    .body(String.class);
            return parseMarketData(json);
        } catch (HttpClientErrorException e) {
            log.debug("Market data fetch failed for {}/{}: {}", engine, market, e.getStatusCode());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<MoexSecurityDto> parseSecurity(String json, String ticker) {
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});

            Map<String, Object> descBlock = (Map<String, Object>) root.get("description");
            if (descBlock == null) return Optional.empty();

            List<String> descCols = (List<String>) descBlock.get("columns");
            List<List<Object>> descData = (List<List<Object>>) descBlock.get("data");
            if (descData == null || descData.isEmpty()) return Optional.empty();

            int nameIdx = MoexJsonMapper.indexOf(descCols, "name");
            int valueIdx = MoexJsonMapper.indexOf(descCols, "value");

            Map<String, String> descMap = buildDescMap(descData, nameIdx, valueIdx);

            Map<String, Object> secBlock = (Map<String, Object>) root.get("securities");
            String boardId = extractPrimaryBoardId(secBlock);
            String name = descMap.getOrDefault("NAME", ticker);

            String group = descMap.get("GROUP");
            SecurityType securityType = MoexSecurityClassifier.fromGroup(group).orElseGet(() -> {
                String typename = descMap.getOrDefault("TYPENAME", "");
                return typename.toLowerCase().contains("акци") ? SecurityType.STOCK : SecurityType.BOND;
            });

            String sector = descMap.get("SECTOR");
            String currency = descMap.get("CURRENCYID");

            return Optional.of(new MoexSecurityDto(ticker, boardId, name, securityType, sector, currency));
        } catch (Exception e) {
            log.error("Failed to parse MOEX security response for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, String> buildDescMap(List<List<Object>> descData, int nameIdx, int valueIdx) {
        Map<String, String> map = new HashMap<>();
        for (List<Object> row : descData) {
            String key = MoexJsonMapper.str(row, nameIdx);
            String val = MoexJsonMapper.str(row, valueIdx);
            if (key != null) {
                map.put(key, val);
            }
        }
        return map;
    }

    private String extractPrimaryBoardId(Map<String, Object> secBlock) {
        if (secBlock == null) return null;
        List<Map<String, Object>> rows = MoexJsonMapper.parseTableFromBlock(secBlock);
        return rows.stream()
                .filter(r -> MoexJsonMapper.boolFromMap(r, "is_primary", 1))
                .map(r -> MoexJsonMapper.strFromMap(r, "BOARDID"))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> rows.stream()
                        .filter(r -> MoexJsonMapper.boolFromMap(r, "is_traded", 1))
                        .map(r -> MoexJsonMapper.strFromMap(r, "BOARDID"))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElseGet(() -> rows.isEmpty() ? null : MoexJsonMapper.strFromMap(rows.get(0), "BOARDID")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, MoexSnapshotDto> parseMarketData(String json) {
        if (json == null) return Collections.emptyMap();
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> mdBlock = (Map<String, Object>) root.get("marketdata");
            if (mdBlock == null) return Collections.emptyMap();

            List<String> cols = (List<String>) mdBlock.get("columns");
            List<List<Object>> data = (List<List<Object>>) mdBlock.get("data");
            if (cols == null || data == null) return Collections.emptyMap();

            int secidIdx = MoexJsonMapper.indexOf(cols, "SECID");
            int lastIdx = MoexJsonMapper.indexOf(cols, "LAST");
            int prevIdx = MoexJsonMapper.indexOf(cols, "PREVPRICE");

            Map<String, MoexSnapshotDto> result = new HashMap<>();
            for (List<Object> row : data) {
                String secid = MoexJsonMapper.str(row, secidIdx);
                BigDecimal last = MoexJsonMapper.decimal(row, lastIdx);
                if (secid == null || last == null || last.compareTo(BigDecimal.ZERO) == 0) continue;
                BigDecimal prev = MoexJsonMapper.decimal(row, prevIdx);
                result.put(secid, new MoexSnapshotDto(secid, last, prev));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse MOEX market data: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<MoexDividendDto> parseDividends(String json) {
        if (json == null) return Collections.emptyList();
        try {
            List<Map<String, Object>> rows = MoexJsonMapper.parseTable(objectMapper, json, "dividends");
            List<MoexDividendDto> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                MoexDividendDto dto = new MoexDividendDto();
                dto.setSecid(MoexJsonMapper.strFromMap(row, "secid"));
                String recordDateStr = MoexJsonMapper.strFromMap(row, "registryclosedate");
                if (recordDateStr != null) {
                    dto.setRegistryCloseDate(LocalDate.parse(recordDateStr));
                }
                dto.setValue(MoexJsonMapper.decimalFromMap(row, "value"));
                dto.setCurrencyId(MoexJsonMapper.strFromMap(row, "currencyid"));
                result.add(dto);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse MOEX dividends response: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<MoexSecurityDto> parseSearchResults(String json) {
        if (json == null) return Collections.emptyList();
        try {
            List<Map<String, Object>> rows = MoexJsonMapper.parseTable(objectMapper, json, "securities");
            List<MoexSecurityDto> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String isTradedStr = MoexJsonMapper.strFromMap(row, "is_traded");
                if (!"1".equals(isTradedStr)) continue;

                String group = MoexJsonMapper.strFromMap(row, "group");
                Optional<SecurityType> typeOpt = MoexSecurityClassifier.fromGroup(group);
                if (typeOpt.isEmpty()) continue;

                String secid = MoexJsonMapper.strFromMap(row, "secid");
                String boardId = MoexJsonMapper.strFromMap(row, "primary_boardid");
                String name = MoexJsonMapper.strFromMap(row, "shortname");
                result.add(new MoexSecurityDto(secid, boardId, name, typeOpt.get(), null, null));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse MOEX search results: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
