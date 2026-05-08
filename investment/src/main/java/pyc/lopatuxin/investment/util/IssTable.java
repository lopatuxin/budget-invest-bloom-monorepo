package pyc.lopatuxin.investment.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class IssTable {

    private final JsonNode columns;
    private final JsonNode data;

    private IssTable(JsonNode columns, JsonNode data) {
        this.columns = columns;
        this.data = data;
    }

    public static Optional<IssTable> of(JsonNode root, String blockName) {
        if (root == null) {
            return Optional.empty();
        }
        JsonNode block = root.get(blockName);
        if (block == null || block.isMissingNode()) {
            return Optional.empty();
        }
        JsonNode cols = block.get("columns");
        JsonNode data = block.get("data");
        if (cols == null || data == null || !cols.isArray() || !data.isArray()) {
            return Optional.empty();
        }
        return Optional.of(new IssTable(cols, data));
    }

    public int columnIndex(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (name.equals(columns.get(i).asText())) {
                return i;
            }
        }
        return -1;
    }

    public Stream<JsonNode> rows() {
        return StreamSupport.stream(data.spliterator(), false);
    }

    public int rowCount() {
        return data.size();
    }

    public JsonNode firstRow() {
        return data.isEmpty() ? null : data.get(0);
    }

    public static String stringAt(JsonNode row, int idx) {
        if (idx < 0 || idx >= row.size()) {
            return null;
        }
        JsonNode node = row.get(idx);
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text.isBlank() ? null : text;
    }

    public static BigDecimal decimalAt(JsonNode row, int idx) {
        if (idx < 0 || idx >= row.size()) {
            return null;
        }
        JsonNode node = row.get(idx);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long longAt(JsonNode row, int idx) {
        if (idx < 0 || idx >= row.size()) {
            return null;
        }
        JsonNode node = row.get(idx);
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText()).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean intEquals(JsonNode row, int idx, int expected) {
        if (idx < 0 || idx >= row.size()) {
            return false;
        }
        JsonNode node = row.get(idx);
        if (node == null || node.isNull()) {
            return false;
        }
        try {
            return Integer.parseInt(node.asText()) == expected;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
