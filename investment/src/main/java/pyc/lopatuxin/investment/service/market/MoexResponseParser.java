package pyc.lopatuxin.investment.service.market;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import pyc.lopatuxin.investment.dto.response.MoexCandleDto;
import pyc.lopatuxin.investment.dto.response.MoexDividendDto;
import pyc.lopatuxin.investment.dto.response.MoexSecurityDto;
import pyc.lopatuxin.investment.dto.response.MoexSnapshotDto;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.util.IssTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
public final class MoexResponseParser {

    private MoexResponseParser() {}

    public static Optional<MoexSecurityDto> parseSecurity(JsonNode root, String ticker) {
        return parseSafely("security", ticker, () -> doParseSecurity(root, ticker), Optional.empty());
    }

    public static List<MoexCandleDto> parseHistoryPage(JsonNode root, String ticker) {
        return parseSafely("history page", ticker, () -> doParseHistoryPage(root, ticker), Collections.emptyList());
    }

    public static int[] parseHistoryCursor(JsonNode root) {
        return parseSafely("history cursor", "root", () -> doParseHistoryCursor(root), null);
    }

    public static Map<String, MoexSnapshotDto> parseMarketData(JsonNode root) {
        return parseSafely("market data", "root", () -> doParseMarketData(root), Collections.emptyMap());
    }

    public static List<MoexDividendDto> parseDividends(JsonNode root) {
        return parseSafely("dividends", "root", () -> doParseDividends(root), Collections.emptyList());
    }

    public static List<MoexSecurityDto> parseBoardSecurities(JsonNode root, SecurityType securityType) {
        return parseSafely("board securities", securityType.name(),
                () -> doParseBoardSecurities(root, securityType), Collections.emptyList());
    }

    public static List<MoexSecurityDto> parseSearchResults(JsonNode root) {
        return parseSafely("search results", "root", () -> doParseSearchResults(root), Collections.emptyList());
    }

    private static <T> T parseSafely(String op, String context, Supplier<T> action, T defaultValue) {
        try {
            return action.get();
        } catch (DateTimeParseException | NumberFormatException | IllegalStateException e) {
            log.warn("Failed to parse MOEX {} for {}: {}", op, context, e.getMessage());
            return defaultValue;
        }
    }

    private static Optional<MoexSecurityDto> doParseSecurity(JsonNode root, String ticker) {
        Optional<IssTable> descOpt = IssTable.of(root, "description");
        if (descOpt.isEmpty()) {
            return Optional.empty();
        }
        IssTable desc = descOpt.get();
        if (desc.rowCount() == 0) {
            return Optional.empty();
        }

        int nameIdx = desc.columnIndex("name");
        int valueIdx = desc.columnIndex("value");
        Map<String, String> descMap = buildDescMap(desc, nameIdx, valueIdx);

        String boardId = extractPrimaryBoardId(root);
        String name = descMap.getOrDefault("NAME", ticker);

        String group = descMap.get("GROUP");
        String moexType = descMap.get("TYPE");
        SecurityType securityType = resolveSecurityType(group, moexType, descMap);

        String sector = resolveSector(ticker, descMap, securityType);
        String currency = descMap.get("CURRENCYID");

        return Optional.of(new MoexSecurityDto(ticker, boardId, name, securityType, sector, currency));
    }

    private static List<MoexCandleDto> doParseHistoryPage(JsonNode root, String ticker) {
        Optional<IssTable> tableOpt = IssTable.of(root, "history");
        if (tableOpt.isEmpty() || tableOpt.get().rowCount() == 0) {
            return Collections.emptyList();
        }
        IssTable table = tableOpt.get();

        int dateIdx   = table.columnIndex("TRADEDATE");
        int openIdx   = table.columnIndex("OPEN");
        int closeIdx  = table.columnIndex("CLOSE");
        int highIdx   = table.columnIndex("HIGH");
        int lowIdx    = table.columnIndex("LOW");
        int volumeIdx = table.columnIndex("VOLUME");

        List<MoexCandleDto> result = new ArrayList<>();
        for (JsonNode row : (Iterable<JsonNode>) table.rows()::iterator) {
            BigDecimal close = IssTable.decimalAt(row, closeIdx);
            String dateStr = IssTable.stringAt(row, dateIdx);
            if (close == null || close.compareTo(BigDecimal.ZERO) == 0 || dateStr == null) {
                continue;
            }
            LocalDate tradeDate = LocalDate.parse(dateStr);
            BigDecimal open   = IssTable.decimalAt(row, openIdx);
            BigDecimal high   = IssTable.decimalAt(row, highIdx);
            BigDecimal low    = IssTable.decimalAt(row, lowIdx);
            Long volume       = IssTable.longAt(row, volumeIdx);
            result.add(new MoexCandleDto(ticker, tradeDate, open, close, high, low, volume));
        }
        return result;
    }

    private static int[] doParseHistoryCursor(JsonNode root) {
        Optional<IssTable> tableOpt = IssTable.of(root, "history.cursor");
        if (tableOpt.isEmpty()) {
            return null;
        }
        IssTable table = tableOpt.get();
        JsonNode row = table.firstRow();
        if (row == null) {
            return null;
        }

        int indexIdx    = table.columnIndex("INDEX");
        int totalIdx    = table.columnIndex("TOTAL");
        int pageSizeIdx = table.columnIndex("PAGESIZE");

        BigDecimal index    = IssTable.decimalAt(row, indexIdx);
        BigDecimal total    = IssTable.decimalAt(row, totalIdx);
        BigDecimal pageSize = IssTable.decimalAt(row, pageSizeIdx);
        if (index == null || total == null || pageSize == null) {
            return null;
        }

        int[] cursor = new int[]{index.intValue(), total.intValue(), pageSize.intValue()};
        if (cursor[2] <= 0) {
            return null;
        }
        return cursor;
    }

    private static Map<String, MoexSnapshotDto> doParseMarketData(JsonNode root) {
        Optional<IssTable> tableOpt = IssTable.of(root, "marketdata");
        if (tableOpt.isEmpty()) {
            return Collections.emptyMap();
        }
        IssTable table = tableOpt.get();

        int secidIdx = table.columnIndex("SECID");
        int lastIdx  = table.columnIndex("LAST");
        int prevIdx  = table.columnIndex("PREVPRICE");

        Map<String, MoexSnapshotDto> result = new HashMap<>();
        for (JsonNode row : (Iterable<JsonNode>) table.rows()::iterator) {
            String secid = IssTable.stringAt(row, secidIdx);
            if (secid == null) {
                continue;
            }
            BigDecimal last = IssTable.decimalAt(row, lastIdx);
            BigDecimal prev = IssTable.decimalAt(row, prevIdx);
            boolean lastEmpty = last == null || last.compareTo(BigDecimal.ZERO) == 0;
            boolean prevEmpty = prev == null || prev.compareTo(BigDecimal.ZERO) == 0;
            if (lastEmpty && prevEmpty) {
                continue;
            }
            result.put(secid, new MoexSnapshotDto(secid, lastEmpty ? null : last, prevEmpty ? null : prev));
        }
        return result;
    }

    private static List<MoexDividendDto> doParseDividends(JsonNode root) {
        Optional<IssTable> tableOpt = IssTable.of(root, "dividends");
        if (tableOpt.isEmpty()) {
            return Collections.emptyList();
        }
        IssTable table = tableOpt.get();

        int secidIdx    = table.columnIndex("secid");
        int dateIdx     = table.columnIndex("registryclosedate");
        int valueIdx    = table.columnIndex("value");
        int currencyIdx = table.columnIndex("currencyid");

        List<MoexDividendDto> result = new ArrayList<>();
        for (JsonNode row : (Iterable<JsonNode>) table.rows()::iterator) {
            MoexDividendDto dto = new MoexDividendDto();
            dto.setSecid(IssTable.stringAt(row, secidIdx));
            String recordDateStr = IssTable.stringAt(row, dateIdx);
            if (recordDateStr != null) {
                dto.setRegistryCloseDate(LocalDate.parse(recordDateStr));
            }
            dto.setValue(IssTable.decimalAt(row, valueIdx));
            dto.setCurrencyId(IssTable.stringAt(row, currencyIdx));
            result.add(dto);
        }
        return result;
    }

    private static List<MoexSecurityDto> doParseBoardSecurities(JsonNode root, SecurityType securityType) {
        Optional<IssTable> tableOpt = IssTable.of(root, "securities");
        if (tableOpt.isEmpty()) {
            return Collections.emptyList();
        }
        IssTable table = tableOpt.get();

        int secidIdx  = table.columnIndex("SECID");
        int boardIdx  = table.columnIndex("BOARDID");
        int nameIdx   = table.columnIndex("SHORTNAME");
        int statusIdx = table.columnIndex("STATUS");

        List<MoexSecurityDto> result = new ArrayList<>();
        for (JsonNode row : (Iterable<JsonNode>) table.rows()::iterator) {
            String status = IssTable.stringAt(row, statusIdx);
            String secid  = IssTable.stringAt(row, secidIdx);
            if (!"A".equals(status) || secid == null) {
                continue;
            }
            String boardId = IssTable.stringAt(row, boardIdx);
            String name    = IssTable.stringAt(row, nameIdx);
            result.add(new MoexSecurityDto(secid, boardId, name, securityType, null, null));
        }
        return result;
    }

    private static List<MoexSecurityDto> doParseSearchResults(JsonNode root) {
        Optional<IssTable> tableOpt = IssTable.of(root, "securities");
        if (tableOpt.isEmpty()) {
            return Collections.emptyList();
        }
        IssTable table = tableOpt.get();

        int isTradedIdx = table.columnIndex("is_traded");
        int groupIdx    = table.columnIndex("group");
        int secidIdx    = table.columnIndex("secid");
        int boardIdx    = table.columnIndex("primary_boardid");
        int nameIdx     = table.columnIndex("shortname");

        List<MoexSecurityDto> result = new ArrayList<>();
        for (JsonNode row : (Iterable<JsonNode>) table.rows()::iterator) {
            String isTraded = IssTable.stringAt(row, isTradedIdx);
            String group = IssTable.stringAt(row, groupIdx);
            Optional<SecurityType> typeOpt = MoexSecurityClassifier.fromGroup(group);
            if (!"1".equals(isTraded) || typeOpt.isEmpty()) {
                continue;
            }
            String secid   = IssTable.stringAt(row, secidIdx);
            String boardId = IssTable.stringAt(row, boardIdx);
            String name    = IssTable.stringAt(row, nameIdx);
            result.add(new MoexSecurityDto(secid, boardId, name, typeOpt.get(), null, null));
        }
        return result;
    }

    private static SecurityType resolveSecurityType(String group, String moexType, Map<String, String> descMap) {
        SecurityType fromGroup = MoexSecurityClassifier.fromGroup(group).orElse(null);

        // If group resolved to BOND but the type field says it's government/OFZ — override
        if (fromGroup == SecurityType.BOND && MoexSecurityClassifier.isOfzType(moexType)) {
            return SecurityType.OFZ;
        }
        if (fromGroup != null) {
            return fromGroup;
        }

        // Fallback: try the type field directly
        SecurityType fromType = MoexSecurityClassifier.fromType(moexType).orElse(null);
        if (fromType != null) {
            return fromType;
        }

        // Last resort: typename heuristic
        String typename = descMap.getOrDefault("TYPENAME", "");
        return typename.toLowerCase().contains("акци") ? SecurityType.STOCK : SecurityType.BOND;
    }

    private static String resolveSector(String ticker, Map<String, String> descMap, SecurityType type) {
        String sector = descMap.get("SECTORNAME");
        if (sector == null || sector.isBlank()) {
            sector = descMap.get("SECTORID");
        }
        if (sector == null || sector.isBlank()) {
            sector = descMap.get("SECTOR");
        }
        if (sector == null || sector.isBlank()) {
            // MOEX does not return sector for stocks — use local dictionary fallback
            sector = SectorDefaults.resolveSector(ticker, type);
        }
        return sector;
    }

    private static Map<String, String> buildDescMap(IssTable table, int nameIdx, int valueIdx) {
        Map<String, String> map = new HashMap<>();
        table.rows().forEach(row -> {
            String key = IssTable.stringAt(row, nameIdx);
            String val = IssTable.stringAt(row, valueIdx);
            if (key != null) {
                map.put(key, val);
            }
        });
        return map;
    }

    private static String extractPrimaryBoardId(JsonNode root) {
        Optional<IssTable> tableOpt = IssTable.of(root, "securities");
        if (tableOpt.isEmpty()) {
            return null;
        }
        IssTable table = tableOpt.get();
        int boardIdx     = table.columnIndex("BOARDID");
        int isPrimaryIdx = table.columnIndex("is_primary");
        int isTradedIdx  = table.columnIndex("is_traded");

        // First: row where is_primary == 1
        Optional<String> primary = table.rows()
                .filter(row -> IssTable.intEquals(row, isPrimaryIdx, 1))
                .map(row -> IssTable.stringAt(row, boardIdx))
                .filter(Objects::nonNull)
                .findFirst();
        if (primary.isPresent()) {
            return primary.get();
        }

        // Second: row where is_traded == 1
        Optional<String> traded = table.rows()
                .filter(row -> IssTable.intEquals(row, isTradedIdx, 1))
                .map(row -> IssTable.stringAt(row, boardIdx))
                .filter(Objects::nonNull)
                .findFirst();
        if (traded.isPresent()) {
            return traded.get();
        }

        // Last resort: first row
        JsonNode firstRow = table.firstRow();
        return firstRow != null ? IssTable.stringAt(firstRow, boardIdx) : null;
    }
}
