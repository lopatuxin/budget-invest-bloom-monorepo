package pyc.lopatuxin.investment.client.moex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.dto.response.MoexCandleDto;
import pyc.lopatuxin.investment.dto.response.MoexSnapshotDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MoexIssClient — 404-сценарии (MoexNotFoundException)")
class MoexIssClientNotFoundTest extends AbstractMoexClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------
    // fetchSnapshots: bonds-market 404 → только данные из shares
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fetchSnapshots — bonds-market 404 → возвращает только данные из shares")
    void fetchSnapshots_returnsSharesData_whenBonds404() throws Exception {
        JsonNode sharesResponse = MAPPER.readTree("""
                {
                  "marketdata": {
                    "columns": ["SECID","LAST","PREVPRICE"],
                    "data": [["SBER","310.50","308.00"]]
                  }
                }
                """);
        when(api.getMarketData(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(sharesResponse)
                .thenThrow(new MoexNotFoundException("MOEX 404: bonds"));

        Map<String, MoexSnapshotDto> result = client.fetchSnapshots(List.of("SBER"));

        assertThat(result).containsOnlyKeys("SBER");
        assertThat(result.get("SBER").lastPrice()).isEqualByComparingTo(new BigDecimal("310.50"));
    }

    @Test
    @DisplayName("fetchSnapshots — shares-market 404 → возвращает только данные из bonds")
    void fetchSnapshots_returnsBondsData_whenShares404() throws Exception {
        JsonNode bondsResponse = MAPPER.readTree("""
                {
                  "marketdata": {
                    "columns": ["SECID","LAST","PREVPRICE"],
                    "data": [["RU000A0JX0J2","1005.00","1000.00"]]
                  }
                }
                """);
        when(api.getMarketData(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new MoexNotFoundException("MOEX 404: shares"))
                .thenReturn(bondsResponse);

        Map<String, MoexSnapshotDto> result = client.fetchSnapshots(List.of("RU000A0JX0J2"));

        assertThat(result).containsOnlyKeys("RU000A0JX0J2");
    }

    @Test
    @DisplayName("fetchSnapshots — shares и bonds оба 404 → пустой результат")
    void fetchSnapshots_returnsEmpty_whenBoth404() {
        when(api.getMarketData(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new MoexNotFoundException("MOEX 404"));

        Map<String, MoexSnapshotDto> result = client.fetchSnapshots(List.of("UNKNOWN"));

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------
    // fetchHistory: 404 от shares → пробует bonds
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fetchHistory — shares 404, bonds возвращает данные → список не пустой")
    void fetchHistory_fallsBackToBonds_whenShares404() throws Exception {
        JsonNode bondsPage = MAPPER.readTree(MoexTestFixtures.buildHistoryJson("LKOH", 0, 1, 100));
        when(api.getHistory(anyString(), anyString(), any(), any(), anyInt(), anyString(), anyString()))
                .thenThrow(new MoexNotFoundException("MOEX 404: shares"))
                .thenReturn(bondsPage);

        List<MoexCandleDto> result = client.fetchHistory("LKOH", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ticker()).isEqualTo("LKOH");
    }

    @Test
    @DisplayName("fetchHistory — shares 404 и bonds 404 → пустой список")
    void fetchHistory_returnsEmpty_whenBoth404() {
        when(api.getHistory(anyString(), anyString(), any(), any(), anyInt(), anyString(), anyString()))
                .thenThrow(new MoexNotFoundException("MOEX 404"));

        List<MoexCandleDto> result = client.fetchHistory("UNKNOWN", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------
    // MoexResilience: MoexNotFoundException НЕ оборачивается в MoexUnavailableException
    // ------------------------------------------------------------------

    @Test
    @DisplayName("MoexResilience.execute — MoexNotFoundException пробрасывается без обёртки в MoexUnavailableException")
    void resilience_propagatesMoexNotFoundException_withoutWrapping() {
        when(api.getSecurity(anyString(), anyString(), anyString()))
                .thenThrow(new MoexNotFoundException("MOEX 404: /securities/NOEXIST.json"));

        assertThatThrownBy(() -> client.fetchSecurity("NOEXIST"))
                .isInstanceOf(MoexNotFoundException.class)
                .isNotInstanceOf(MoexUnavailableException.class);
    }
}
