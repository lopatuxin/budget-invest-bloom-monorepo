package pyc.lopatuxin.investment.service.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pyc.lopatuxin.investment.dto.response.MoexCandleDto;
import pyc.lopatuxin.investment.dto.response.MoexDividendDto;
import pyc.lopatuxin.investment.dto.response.MoexSnapshotDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoexResponseParser — unit-тесты статических методов парсинга")
class MoexResponseParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------
    // parseHistoryCursor
    // ------------------------------------------------------------------

    @Test
    @DisplayName("parseHistoryCursor — pageSize=0 → null (защита от вечного цикла)")
    void parseHistoryCursor_returnsNull_whenPageSizeIsZero() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "history.cursor": {
                    "columns": ["INDEX","TOTAL","PAGESIZE"],
                    "data": [[0,100,0]]
                  }
                }
                """);

        int[] cursor = MoexResponseParser.parseHistoryCursor(root);

        assertThat(cursor).isNull();
    }

    @Test
    @DisplayName("parseHistoryCursor — pageSize=-1 → null")
    void parseHistoryCursor_returnsNull_whenPageSizeIsNegative() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "history.cursor": {
                    "columns": ["INDEX","TOTAL","PAGESIZE"],
                    "data": [[0,100,-1]]
                  }
                }
                """);

        int[] cursor = MoexResponseParser.parseHistoryCursor(root);

        assertThat(cursor).isNull();
    }

    @Test
    @DisplayName("parseHistoryCursor — корректные данные возвращает массив [index, total, pageSize]")
    void parseHistoryCursor_returnsArray_whenDataValid() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "history.cursor": {
                    "columns": ["INDEX","TOTAL","PAGESIZE"],
                    "data": [[50,200,100]]
                  }
                }
                """);

        int[] cursor = MoexResponseParser.parseHistoryCursor(root);

        assertThat(cursor).isNotNull().containsExactly(50, 200, 100);
    }

    @Test
    @DisplayName("parseHistoryCursor — блок history.cursor отсутствует → null")
    void parseHistoryCursor_returnsNull_whenBlockMissing() throws Exception {
        JsonNode root = MAPPER.readTree("{}");

        int[] cursor = MoexResponseParser.parseHistoryCursor(root);

        assertThat(cursor).isNull();
    }

    @Test
    @DisplayName("parseHistoryCursor — пустой data массив → null")
    void parseHistoryCursor_returnsNull_whenDataEmpty() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "history.cursor": {
                    "columns": ["INDEX","TOTAL","PAGESIZE"],
                    "data": []
                  }
                }
                """);

        int[] cursor = MoexResponseParser.parseHistoryCursor(root);

        assertThat(cursor).isNull();
    }

    // ------------------------------------------------------------------
    // parseHistoryPage — malformed TRADEDATE → пустой список
    // ------------------------------------------------------------------

    @Test
    @DisplayName("parseHistoryPage — TRADEDATE невалидный формат → пустой список (DateTimeParseException перехватывается)")
    void parseHistoryPage_returnsEmpty_whenTradeDateMalformed() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "history": {
                    "columns": ["BOARDID","TRADEDATE","SHORTNAME","SECID","NUMTRADES","VALUE","OPEN","LOW","HIGH","LEGALCLOSEPRICE","WAPRICE","CLOSE","VOLUME","MARKETPRICE2","MARKETPRICE3","ADMITTEDQUOTE","MP2VALTRD","MARKETPRICE3TRADESVALUE","ADMITTEDVALUE","WAVAL","TRADINGSESSION"],
                    "data": [
                      ["TQBR","not-a-date","SBER","SBER",100,270000.0,270.0,268.0,272.0,271.0,270.5,271.0,1000,null,null,null,null,null,null,null,1]
                    ]
                  }
                }
                """);

        List<MoexCandleDto> result = MoexResponseParser.parseHistoryPage(root, "SBER");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parseHistoryPage — блок history отсутствует → пустой список")
    void parseHistoryPage_returnsEmpty_whenBlockMissing() throws Exception {
        JsonNode root = MAPPER.readTree("{}");

        List<MoexCandleDto> result = MoexResponseParser.parseHistoryPage(root, "SBER");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parseHistoryPage — строка с close=null пропускается")
    void parseHistoryPage_skipsRowWithNullClose() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "history": {
                    "columns": ["BOARDID","TRADEDATE","SHORTNAME","SECID","NUMTRADES","VALUE","OPEN","LOW","HIGH","LEGALCLOSEPRICE","WAPRICE","CLOSE","VOLUME","MARKETPRICE2","MARKETPRICE3","ADMITTEDQUOTE","MP2VALTRD","MARKETPRICE3TRADESVALUE","ADMITTEDVALUE","WAVAL","TRADINGSESSION"],
                    "data": [
                      ["TQBR","2024-01-10","SBER","SBER",100,270000.0,270.0,268.0,272.0,null,270.5,null,1000,null,null,null,null,null,null,null,1],
                      ["TQBR","2024-01-15","SBER","SBER",200,540000.0,270.0,268.0,272.0,271.0,270.5,271.0,2000,null,null,null,null,null,null,null,1]
                    ]
                  }
                }
                """);

        List<MoexCandleDto> result = MoexResponseParser.parseHistoryPage(root, "SBER");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tradeDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("parseHistoryPage — строка с close=0 пропускается")
    void parseHistoryPage_skipsRowWithZeroClose() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "history": {
                    "columns": ["BOARDID","TRADEDATE","SHORTNAME","SECID","NUMTRADES","VALUE","OPEN","LOW","HIGH","LEGALCLOSEPRICE","WAPRICE","CLOSE","VOLUME","MARKETPRICE2","MARKETPRICE3","ADMITTEDQUOTE","MP2VALTRD","MARKETPRICE3TRADESVALUE","ADMITTEDVALUE","WAVAL","TRADINGSESSION"],
                    "data": [
                      ["TQBR","2024-01-10","SBER","SBER",0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,null,null,null,null,null,null,null,1],
                      ["TQBR","2024-01-15","SBER","SBER",200,540000.0,270.0,268.0,272.0,271.0,270.5,271.0,2000,null,null,null,null,null,null,null,1]
                    ]
                  }
                }
                """);

        List<MoexCandleDto> result = MoexResponseParser.parseHistoryPage(root, "SBER");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).close()).isEqualByComparingTo(new BigDecimal("271.0"));
    }

    // ------------------------------------------------------------------
    // parseMarketData
    // ------------------------------------------------------------------

    @Test
    @DisplayName("parseMarketData — строки с last=0 и prev=0 пропускаются")
    void parseMarketData_skipsRowWithBothPricesZero() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "marketdata": {
                    "columns": ["SECID","LAST","PREVPRICE"],
                    "data": [
                      ["ZERO","0","0"],
                      ["SBER","310.50","308.00"]
                    ]
                  }
                }
                """);

        Map<String, MoexSnapshotDto> result = MoexResponseParser.parseMarketData(root);

        assertThat(result).containsOnlyKeys("SBER");
        assertThat(result.get("SBER").lastPrice()).isEqualByComparingTo(new BigDecimal("310.50"));
    }

    @Test
    @DisplayName("parseMarketData — last=null, prev=5 → lastPrice=null, previousClose=5")
    void parseMarketData_usesPrevclosWhenLastIsNull() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "marketdata": {
                    "columns": ["SECID","LAST","PREVPRICE"],
                    "data": [["SBER",null,"308.00"]]
                  }
                }
                """);

        Map<String, MoexSnapshotDto> result = MoexResponseParser.parseMarketData(root);

        assertThat(result).containsKey("SBER");
        assertThat(result.get("SBER").lastPrice()).isNull();
        assertThat(result.get("SBER").previousClose()).isEqualByComparingTo(new BigDecimal("308.00"));
    }

    // ------------------------------------------------------------------
    // parseDividends
    // ------------------------------------------------------------------

    @Test
    @DisplayName("parseDividends — два валидных дивиденда → список из двух элементов")
    void parseDividends_returnsList() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "dividends": {
                    "columns": ["secid","registryclosedate","value","currencyid"],
                    "data": [
                      ["SBER","2023-05-15",25.0,"RUB"],
                      ["SBER","2022-05-20",18.7,"RUB"]
                    ]
                  }
                }
                """);

        List<MoexDividendDto> result = MoexResponseParser.parseDividends(root);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSecid()).isEqualTo("SBER");
        assertThat(result.get(0).getRegistryCloseDate()).isEqualTo(LocalDate.of(2023, 5, 15));
        assertThat(result.get(0).getValue()).isEqualByComparingTo(new BigDecimal("25.0"));
        assertThat(result.get(0).getCurrencyId()).isEqualTo("RUB");
        assertThat(result.get(1).getRegistryCloseDate()).isEqualTo(LocalDate.of(2022, 5, 20));
    }

    @Test
    @DisplayName("parseDividends — блок dividends отсутствует → пустой список")
    void parseDividends_returnsEmpty_whenBlockMissing() throws Exception {
        JsonNode root = MAPPER.readTree("{}");

        List<MoexDividendDto> result = MoexResponseParser.parseDividends(root);

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------
    // parseSecurity
    // ------------------------------------------------------------------

    @Test
    @DisplayName("parseSecurity — description пустой → Optional.empty()")
    void parseSecurity_returnsEmpty_whenDescriptionEmpty() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "description": {"columns": ["name","title","value"], "data": []},
                  "securities": {"columns": ["SECID","BOARDID","SHORTNAME","is_primary"], "data": []}
                }
                """);

        var result = MoexResponseParser.parseSecurity(root, "UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parseSecurity — group=stock_shares → SecurityType.STOCK")
    void parseSecurity_resolvesStockType() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "description": {
                    "columns": ["name","title","value"],
                    "data": [
                      ["SECID","Код","SBER"],
                      ["NAME","Наименование","Сбербанк"],
                      ["GROUP","Группа","stock_shares"],
                      ["CURRENCYID","Валюта","RUB"]
                    ]
                  },
                  "securities": {
                    "columns": ["SECID","BOARDID","SHORTNAME","is_primary","is_traded"],
                    "data": [["SBER","TQBR","Сбербанк",1,1]]
                  }
                }
                """);

        var result = MoexResponseParser.parseSecurity(root, "SBER");

        assertThat(result).isPresent();
        assertThat(result.get().securityType()).isEqualTo(pyc.lopatuxin.investment.entity.enums.SecurityType.STOCK);
        assertThat(result.get().boardId()).isEqualTo("TQBR");
        assertThat(result.get().currency()).isEqualTo("RUB");
    }

    // ------------------------------------------------------------------
    // Параметризованные: cursor pageSize <= 0
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "pageSize={0} → null")
    @CsvSource({"0", "-1", "-100"})
    @DisplayName("parseHistoryCursor — pageSize <= 0 всегда возвращает null")
    void parseHistoryCursor_returnsNull_whenPageSizeNotPositive(int pageSize) throws Exception {
        String json = String.format("""
                {
                  "history.cursor": {
                    "columns": ["INDEX","TOTAL","PAGESIZE"],
                    "data": [[0,100,%d]]
                  }
                }
                """, pageSize);
        JsonNode root = MAPPER.readTree(json);

        int[] cursor = MoexResponseParser.parseHistoryCursor(root);

        assertThat(cursor).isNull();
    }
}
