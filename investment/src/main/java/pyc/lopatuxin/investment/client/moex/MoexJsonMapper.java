package pyc.lopatuxin.investment.client.moex;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class MoexJsonMapper {

    private MoexJsonMapper() {
    }

    static int indexOf(List<String> columns, String name) {
        return columns.indexOf(name);
    }

    static String str(List<Object> row, int idx) {
        if (idx < 0 || idx >= row.size()) return null;
        Object val = row.get(idx);
        return val != null ? val.toString() : null;
    }

    static BigDecimal decimal(List<Object> row, int idx) {
        if (idx < 0 || idx >= row.size()) return null;
        Object val = row.get(idx);
        if (val == null) return null;
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> parseTable(ObjectMapper om, String json, String blockName) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            Map<String, Object> root = om.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            Map<String, Object> block = (Map<String, Object>) root.get(blockName);
            return parseTableFromBlock(block);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> parseTableFromBlock(Map<String, Object> block) {
        if (block == null) return Collections.emptyList();
        List<String> cols = (List<String>) block.get("columns");
        List<List<Object>> rows = (List<List<Object>>) block.get("data");
        if (cols == null || rows == null) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (List<Object> row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < cols.size(); i++) {
                map.put(cols.get(i), i < row.size() ? row.get(i) : null);
            }
            result.add(map);
        }
        return result;
    }

    static String strFromMap(Map<String, Object> row, String col) {
        Object v = row.get(col);
        if (v == null) return null;
        String s = v.toString();
        return s.isBlank() ? null : s;
    }

    static BigDecimal decimalFromMap(Map<String, Object> row, String col) {
        Object v = row.get(col);
        if (v == null) return null;
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean boolFromMap(Map<String, Object> row, String col, int trueValue) {
        Object v = row.get(col);
        if (v == null) return false;
        try {
            return Integer.parseInt(v.toString()) == trueValue;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
