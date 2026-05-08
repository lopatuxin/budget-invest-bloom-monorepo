package pyc.lopatuxin.investment.client.moex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.dto.response.MoexCandleDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MoexIssClientParseHistoryPageTest — guard-ветки parseHistoryPage через client")
class MoexIssClientParseHistoryPageTest extends AbstractMoexClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------
    // close == null
    // ------------------------------------------------------------------

    @Test
    @DisplayName("parseHistoryPage — строка с close=null пропускается, остальные возвращаются")
    void fetchHistory_rowWithNullClose_isSkipped() throws Exception {
        JsonNode response = MAPPER.readTree(MoexTestFixtures.buildHistoryJsonWithRows(
                "[\"TQBR\",\"2024-01-10\",\"SBER\",\"SBER\",100,270000.0,270.0,268.0,272.0,null,270.5,null,1000,null,null,null,null,null,null,null,1]",
                "[\"TQBR\",\"2024-01-15\",\"SBER\",\"SBER\",200,540000.0,270.0,268.0,272.0,271.0,270.5,271.0,2000,null,null,null,null,null,null,null,1]"
        ));
        when(api.getHistory(anyString(), anyString(), any(), any(), anyInt(), anyString(), anyString()))
                .thenReturn(response);

        List<MoexCandleDto> result = client.fetchHistory("SBER", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tradeDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(result.get(0).close()).isEqualByComparingTo(new BigDecimal("271.0"));
    }

    // ------------------------------------------------------------------
    // close == 0
    // ------------------------------------------------------------------

    @Test
    @DisplayName("parseHistoryPage — строка с close=0 пропускается, остальные возвращаются")
    void fetchHistory_rowWithZeroClose_isSkipped() throws Exception {
        JsonNode response = MAPPER.readTree(MoexTestFixtures.buildHistoryJsonWithRows(
                "[\"TQBR\",\"2024-01-10\",\"SBER\",\"SBER\",0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,null,null,null,null,null,null,null,1]",
                "[\"TQBR\",\"2024-01-15\",\"SBER\",\"SBER\",200,540000.0,270.0,268.0,272.0,271.0,270.5,271.0,2000,null,null,null,null,null,null,null,1]"
        ));
        when(api.getHistory(anyString(), anyString(), any(), any(), anyInt(), anyString(), anyString()))
                .thenReturn(response);

        List<MoexCandleDto> result = client.fetchHistory("SBER", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).close()).isEqualByComparingTo(new BigDecimal("271.0"));
    }

    // ------------------------------------------------------------------
    // dateStr == null
    // ------------------------------------------------------------------

    @Test
    @DisplayName("parseHistoryPage — строка с dateStr=null пропускается, остальные возвращаются")
    void fetchHistory_rowWithNullDate_isSkipped() throws Exception {
        JsonNode response = MAPPER.readTree(MoexTestFixtures.buildHistoryJsonWithRows(
                "[\"TQBR\",null,\"SBER\",\"SBER\",100,270000.0,270.0,268.0,272.0,271.0,270.5,271.0,1000,null,null,null,null,null,null,null,1]",
                "[\"TQBR\",\"2024-01-15\",\"SBER\",\"SBER\",200,540000.0,270.0,268.0,272.0,271.0,270.5,271.0,2000,null,null,null,null,null,null,null,1]"
        ));
        when(api.getHistory(anyString(), anyString(), any(), any(), anyInt(), anyString(), anyString()))
                .thenReturn(response);

        List<MoexCandleDto> result = client.fetchHistory("SBER", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tradeDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    // ------------------------------------------------------------------
    // All bad rows — shares empty → bonds tried → mock returns empty too
    // ------------------------------------------------------------------

    @Test
    @DisplayName("parseHistoryPage — все строки с невалидными close/date → результат пустой")
    void fetchHistory_allRowsInvalid_returnsEmpty() throws Exception {
        JsonNode allBadRows = MAPPER.readTree(MoexTestFixtures.buildHistoryJsonWithRows(
                "[\"TQBR\",\"2024-01-10\",\"SBER\",\"SBER\",0,0.0,0.0,0.0,0.0,0.0,0.0,null,0,null,null,null,null,null,null,null,1]",
                "[\"TQBR\",\"2024-01-11\",\"SBER\",\"SBER\",0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0,null,null,null,null,null,null,null,1]",
                "[\"TQBR\",null,\"SBER\",\"SBER\",0,0.0,0.0,0.0,0.0,0.0,0.0,271.0,0,null,null,null,null,null,null,null,1]"
        ));
        JsonNode emptyBonds = MAPPER.readTree(MoexTestFixtures.buildEmptyHistoryJson());
        // shares → all filtered, bonds → empty
        when(api.getHistory(anyString(), anyString(), any(), any(), anyInt(), anyString(), anyString()))
                .thenReturn(allBadRows)
                .thenReturn(emptyBonds);

        List<MoexCandleDto> result = client.fetchHistory("SBER", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).isEmpty();
    }
}
