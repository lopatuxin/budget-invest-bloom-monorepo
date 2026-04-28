package pyc.lopatuxin.investment.client.moex;

import java.math.BigDecimal;
import java.util.List;

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
}
