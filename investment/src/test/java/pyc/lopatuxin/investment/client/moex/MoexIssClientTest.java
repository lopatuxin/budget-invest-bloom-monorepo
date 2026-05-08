package pyc.lopatuxin.investment.client.moex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.dto.response.MoexCandleDto;
import pyc.lopatuxin.investment.dto.response.MoexSecurityDto;
import pyc.lopatuxin.investment.dto.response.MoexSnapshotDto;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MoexIssClientTest — юнит-тесты с Mockito")
class MoexIssClientTest extends AbstractMoexClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("fetchSecurity — возвращает корректный MoexSecurityDto для SBER")
    void fetchSecurity_returnsDtoWhenDataPresent() throws Exception {
        JsonNode response = MAPPER.readTree("""
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
        when(api.getSecurity(anyString(), anyString(), anyString())).thenReturn(response);

        var result = client.fetchSecurity("SBER");

        assertThat(result).isPresent();
        MoexSecurityDto dto = result.get();
        assertThat(dto.ticker()).isEqualTo("SBER");
        assertThat(dto.name()).isEqualTo("Сбербанк");
        assertThat(dto.boardId()).isEqualTo("TQBR");
        assertThat(dto.securityType()).isEqualTo(SecurityType.STOCK);
        assertThat(dto.currency()).isEqualTo("RUB");
    }

    @Test
    @DisplayName("fetchSnapshots — возвращает Map с lastPrice для SBER")
    void fetchSnapshots_returnsMapWithLastPrice() throws Exception {
        JsonNode sharesResponse = MAPPER.readTree("""
                {
                  "marketdata": {
                    "columns": ["SECID","LAST","PREVPRICE"],
                    "data": [["SBER","310.50","308.00"]]
                  }
                }
                """);
        JsonNode bondsResponse = MAPPER.readTree("""
                {"marketdata":{"columns":["SECID","LAST","PREVPRICE"],"data":[]}}
                """);
        when(api.getMarketData(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sharesResponse)
                .thenReturn(bondsResponse);

        var result = client.fetchSnapshots(List.of("SBER"));

        assertThat(result).containsKey("SBER");
        MoexSnapshotDto snap = result.get("SBER");
        assertThat(snap.lastPrice()).isEqualByComparingTo(new BigDecimal("310.50"));
        assertThat(snap.previousClose()).isEqualByComparingTo(new BigDecimal("308.00"));
    }

    @Test
    @DisplayName("fetchSecurity — SBER без SECTOR в MOEX-ответе → sector = 'Финансы' из локального справочника")
    void fetchSecurity_resolvesSectorFromLocalDictionaryWhenMoexHasNone() throws Exception {
        JsonNode response = MAPPER.readTree("""
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
        when(api.getSecurity(anyString(), anyString(), anyString())).thenReturn(response);

        var result = client.fetchSecurity("SBER");

        assertThat(result).isPresent();
        assertThat(result.get().sector()).isEqualTo("Финансы");
    }

    @Test
    @DisplayName("fetchSecurity — пустой data блок возвращает Optional.empty()")
    void fetchSecurity_returnsEmptyWhenDataEmpty() throws Exception {
        JsonNode response = MAPPER.readTree("""
                {
                  "description": {"columns": ["name","title","value"], "data": []},
                  "securities": {"columns": ["SECID","BOARDID","SHORTNAME"], "data": []}
                }
                """);
        when(api.getSecurity(anyString(), anyString(), anyString())).thenReturn(response);

        var result = client.fetchSecurity("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchHistory — парсит первую страницу, возвращает корректные MoexCandleDto")
    void fetchHistory_parsesFirstPage() throws Exception {
        JsonNode response = MAPPER.readTree(MoexTestFixtures.buildHistoryJson("SBER", 0, 1, 100));
        when(api.getHistory(anyString(), anyString(), any(), any(), anyInt(), anyString(), anyString()))
                .thenReturn(response);

        List<MoexCandleDto> result = client.fetchHistory("SBER", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).hasSize(1);
        MoexCandleDto candle = result.get(0);
        assertThat(candle.ticker()).isEqualTo("SBER");
        assertThat(candle.tradeDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(candle.close()).isEqualByComparingTo(new BigDecimal("271.0"));
        assertThat(candle.open()).isEqualByComparingTo(new BigDecimal("270.0"));
        assertThat(candle.volume()).isEqualTo(11000L);
    }

    @Test
    @DisplayName("fetchHistory — пагинация: два запроса при TOTAL=200, PAGESIZE=100")
    void fetchHistory_paginates() throws Exception {
        JsonNode page1 = MAPPER.readTree(MoexTestFixtures.buildHistoryJson("SBER", 0, 200, 100));
        JsonNode page2 = MAPPER.readTree(MoexTestFixtures.buildHistoryJson("SBER", 100, 200, 100));
        when(api.getHistory(anyString(), anyString(), any(), any(), anyInt(), anyString(), anyString()))
                .thenReturn(page1)
                .thenReturn(page2);

        List<MoexCandleDto> result = client.fetchHistory("SBER", LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("fetchHistory — shares пустой, bonds возвращает данные")
    void fetchHistory_fallsBackToBonds() throws Exception {
        JsonNode emptyShares = MAPPER.readTree(MoexTestFixtures.buildEmptyHistoryJson());
        JsonNode bondsPage = MAPPER.readTree(MoexTestFixtures.buildHistoryJson("LKOH", 0, 1, 100));
        when(api.getHistory(anyString(), anyString(), any(), any(), anyInt(), anyString(), anyString()))
                .thenReturn(emptyShares)
                .thenReturn(bondsPage);

        List<MoexCandleDto> result = client.fetchHistory("LKOH", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ticker()).isEqualTo("LKOH");
    }

    @Test
    @DisplayName("fetchHistory — API бросает RuntimeException → MoexUnavailableException")
    void fetchHistory_throwsOnUnavailable() {
        when(api.getHistory(anyString(), anyString(), any(), any(), anyInt(), anyString(), anyString()))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> client.fetchHistory("SBER", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .isInstanceOf(MoexUnavailableException.class);
    }
}
