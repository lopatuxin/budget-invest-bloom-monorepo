package pyc.lopatuxin.investment.service.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pyc.lopatuxin.investment.dto.response.MoexCandleDto;
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
                    "columns": ["SECID","LAST"],
                    "data": [
                      ["ZERO","0"],
                      ["SBER","310.50"]
                    ]
                  },
                  "securities": {
                    "columns": ["SECID","PREVPRICE"],
                    "data": [
                      ["ZERO","0"],
                      ["SBER","308.00"]
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
                    "columns": ["SECID","LAST"],
                    "data": [["SBER",null]]
                  },
                  "securities": {
                    "columns": ["SECID","PREVPRICE"],
                    "data": [["SBER","308.00"]]
                  }
                }
                """);

        Map<String, MoexSnapshotDto> result = MoexResponseParser.parseMarketData(root);

        assertThat(result).containsKey("SBER");
        assertThat(result.get("SBER").lastPrice()).isNull();
        assertThat(result.get("SBER").previousClose()).isEqualByComparingTo(new BigDecimal("308.00"));
    }

    @Test
    @DisplayName("parseMarketData — PREVPRICE лежит в блоке securities, а не marketdata, как в настоящем ответе биржи")
    void parseMarketData_readsPreviousCloseFromSecuritiesBlock_realMoexResponseShape() throws Exception {
        // Trimmed capture of a real MOEX /engines/stock/markets/shares/securities.json response
        // for SBER (iss.only=marketdata,securities): PREVPRICE is a "securities" column, absent
        // from "marketdata" entirely. Two boards are listed (SPEQ, TQBR) for the same SECID in
        // both tables in the same order — TQBR is the tradeable board and comes last.
        JsonNode root = MAPPER.readTree("""
                {
                  "marketdata": {
                    "columns": ["SECID","BOARDID","LAST","TRADINGSTATUS"],
                    "data": [
                      ["SBER","SPEQ",null,"T"],
                      ["SBER","TQBR",278.83,"T"]
                    ]
                  },
                  "securities": {
                    "columns": ["SECID","BOARDID","SHORTNAME","PREVPRICE","STATUS"],
                    "data": [
                      ["SBER","SPEQ","Сбербанк",315.28,"A"],
                      ["SBER","TQBR","Сбербанк",280.68,"A"]
                    ]
                  }
                }
                """);

        Map<String, MoexSnapshotDto> result = MoexResponseParser.parseMarketData(root);

        assertThat(result).containsKey("SBER");
        assertThat(result.get("SBER").lastPrice()).isEqualByComparingTo(new BigDecimal("278.83"));
        assertThat(result.get("SBER").previousClose()).isEqualByComparingTo(new BigDecimal("280.68"));
    }

    @Test
    @DisplayName("parseMarketData — блок securities отсутствует: LAST уцелевает, previousClose = null, без падения")
    void parseMarketData_securitiesBlockMissing_lastPriceSurvivesPreviousCloseNull() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "marketdata": {
                    "columns": ["SECID","LAST"],
                    "data": [["SBER",278.83]]
                  }
                }
                """);

        Map<String, MoexSnapshotDto> result = MoexResponseParser.parseMarketData(root);

        assertThat(result).containsKey("SBER");
        assertThat(result.get("SBER").lastPrice()).isEqualByComparingTo(new BigDecimal("278.83"));
        assertThat(result.get("SBER").previousClose()).isNull();
    }

    @Test
    @DisplayName("parseMarketData — второй режим торгов без PREVPRICE не затирает корректное значение из первого")
    void parseMarketData_secondBoardWithEmptyPrevPrice_doesNotOverwriteValidOne() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "marketdata": {
                    "columns": ["SECID","BOARDID","LAST"],
                    "data": [
                      ["SBER","TQBR",278.83],
                      ["SBER","SPEQ",279.00]
                    ]
                  },
                  "securities": {
                    "columns": ["SECID","BOARDID","PREVPRICE"],
                    "data": [
                      ["SBER","TQBR",280.68],
                      ["SBER","SPEQ",0]
                    ]
                  }
                }
                """);

        Map<String, MoexSnapshotDto> result = MoexResponseParser.parseMarketData(root);

        assertThat(result.get("SBER").previousClose()).isEqualByComparingTo(new BigDecimal("280.68"));
    }

    @Test
    @DisplayName("parseMarketData — FACEVALUE и ACCRUEDINT облигационной доски читаются в MoexSnapshotDto")
    void parseMarketData_bondBoard_readsFaceValueAndAccruedInterest() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "marketdata": {
                    "columns": ["SECID","LAST"],
                    "data": [["SU26219RMFS4",99.95]]
                  },
                  "securities": {
                    "columns": ["SECID","PREVPRICE","FACEVALUE","ACCRUEDINT"],
                    "data": [["SU26219RMFS4",99.80,1000,12.34]]
                  }
                }
                """);

        Map<String, MoexSnapshotDto> result = MoexResponseParser.parseMarketData(root);

        MoexSnapshotDto dto = result.get("SU26219RMFS4");
        assertThat(dto.lastPrice()).isEqualByComparingTo("99.95");
        assertThat(dto.faceValue()).isEqualByComparingTo("1000");
        assertThat(dto.accruedInterest()).isEqualByComparingTo("12.34");
    }

    @Test
    @DisplayName("parseMarketData — акции: FACEVALUE/ACCRUEDINT отсутствуют в ответе → null, без падения")
    void parseMarketData_sharesBoard_faceValueAndAccruedInterestAreNull() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "marketdata": {
                    "columns": ["SECID","LAST"],
                    "data": [["SBER",278.83]]
                  },
                  "securities": {
                    "columns": ["SECID","PREVPRICE"],
                    "data": [["SBER",280.68]]
                  }
                }
                """);

        Map<String, MoexSnapshotDto> result = MoexResponseParser.parseMarketData(root);

        MoexSnapshotDto dto = result.get("SBER");
        assertThat(dto.faceValue()).isNull();
        assertThat(dto.accruedInterest()).isNull();
    }

    @Test
    @DisplayName("parseMarketData — ACCRUEDINT=0 (день после купона) сохраняется как ноль, а не как отсутствие данных")
    void parseMarketData_zeroAccruedInterest_keptAsZeroNotNull() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "marketdata": {
                    "columns": ["SECID","LAST"],
                    "data": [["SU26219RMFS4",100.00]]
                  },
                  "securities": {
                    "columns": ["SECID","PREVPRICE","FACEVALUE","ACCRUEDINT"],
                    "data": [["SU26219RMFS4",99.80,1000,0]]
                  }
                }
                """);

        Map<String, MoexSnapshotDto> result = MoexResponseParser.parseMarketData(root);

        assertThat(result.get("SU26219RMFS4").accruedInterest()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("parseMarketData — FACEVALUE=0 трактуется как отсутствующее значение (не бывает у настоящей облигации)")
    void parseMarketData_zeroFaceValue_treatedAsMissing() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "marketdata": {
                    "columns": ["SECID","LAST"],
                    "data": [["SU26219RMFS4",100.00]]
                  },
                  "securities": {
                    "columns": ["SECID","FACEVALUE"],
                    "data": [["SU26219RMFS4",0]]
                  }
                }
                """);

        Map<String, MoexSnapshotDto> result = MoexResponseParser.parseMarketData(root);

        assertThat(result.get("SU26219RMFS4").faceValue()).isNull();
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
                  "boards": {"columns": ["secid","boardid","is_primary"], "data": []}
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
                  "boards": {
                    "columns": ["secid","boardid","is_primary","is_traded"],
                    "data": [["SBER","TQBR",1,1]]
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
