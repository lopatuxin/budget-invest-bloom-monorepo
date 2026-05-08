package pyc.lopatuxin.investment.client.moex;

/**
 * Static test fixtures shared across MoexIssClient unit test classes.
 */
final class MoexTestFixtures {

    private MoexTestFixtures() {
    }

    /**
     * Builds a MOEX history JSON response with one data row for the given ticker,
     * using the supplied cursor values (index / total / pageSize).
     */
    static String buildHistoryJson(String ticker, int index, int total, int pageSize) {
        return """
                {
                  "history": {
                    "columns": ["BOARDID","TRADEDATE","SHORTNAME","SECID","NUMTRADES","VALUE","OPEN","LOW","HIGH","LEGALCLOSEPRICE","WAPRICE","CLOSE","VOLUME","MARKETPRICE2","MARKETPRICE3","ADMITTEDQUOTE","MP2VALTRD","MARKETPRICE3TRADESVALUE","ADMITTEDVALUE","WAVAL","TRADINGSESSION"],
                    "data": [
                      ["TQBR","2024-01-15","%s","%s",12000,3000000.0,270.0,268.0,272.0,271.0,270.5,271.0,11000,null,null,null,null,null,null,null,1]
                    ]
                  },
                  "history.cursor": {
                    "columns": ["INDEX","TOTAL","PAGESIZE"],
                    "data": [[%d,%d,%d]]
                  }
                }
                """.formatted(ticker, ticker, index, total, pageSize);
    }

    /**
     * Builds a MOEX history JSON response with an empty data array
     * (cursor: index=0, total=0, pageSize=100).
     */
    static String buildEmptyHistoryJson() {
        return """
                {
                  "history": {
                    "columns": ["BOARDID","TRADEDATE","SHORTNAME","SECID","NUMTRADES","VALUE","OPEN","LOW","HIGH","LEGALCLOSEPRICE","WAPRICE","CLOSE","VOLUME","MARKETPRICE2","MARKETPRICE3","ADMITTEDQUOTE","MP2VALTRD","MARKETPRICE3TRADESVALUE","ADMITTEDVALUE","WAVAL","TRADINGSESSION"],
                    "data": []
                  },
                  "history.cursor": {
                    "columns": ["INDEX","TOTAL","PAGESIZE"],
                    "data": [[0,0,100]]
                  }
                }
                """;
    }

    /**
     * Builds a MOEX history JSON response with custom rows and cursor (index=0, total=1, pageSize=100).
     * Each element of {@code rows} is a JSON array string for a single data row.
     */
    static String buildHistoryJsonWithRows(String... rows) {
        String dataJoined = String.join(",\n      ", rows);
        return """
                {
                  "history": {
                    "columns": ["BOARDID","TRADEDATE","SHORTNAME","SECID","NUMTRADES","VALUE","OPEN","LOW","HIGH","LEGALCLOSEPRICE","WAPRICE","CLOSE","VOLUME","MARKETPRICE2","MARKETPRICE3","ADMITTEDQUOTE","MP2VALTRD","MARKETPRICE3TRADESVALUE","ADMITTEDVALUE","WAVAL","TRADINGSESSION"],
                    "data": [
                      %s
                    ]
                  },
                  "history.cursor": {
                    "columns": ["INDEX","TOTAL","PAGESIZE"],
                    "data": [[0,1,100]]
                  }
                }
                """.formatted(dataJoined);
    }
}
