package pyc.lopatuxin.investment.client.moex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.dto.response.MoexDividendDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MoexIssClientFetchDividendsTest — юнит-тесты с Mockito")
class MoexIssClientFetchDividendsTest extends AbstractMoexClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("fetchDividends — MOEX вернул два дивиденда → список содержит 2 элемента с корректными полями")
    void fetchDividends_returnsListFromMoex() throws Exception {
        JsonNode response = MAPPER.readTree("""
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
        when(api.getDividends(anyString(), anyString(), anyString())).thenReturn(response);

        List<MoexDividendDto> result = client.fetchDividends("SBER");

        assertThat(result).hasSize(2);

        MoexDividendDto first = result.get(0);
        assertThat(first.getSecid()).isEqualTo("SBER");
        assertThat(first.getRegistryCloseDate()).isEqualTo(LocalDate.of(2023, 5, 15));
        assertThat(first.getValue()).isEqualByComparingTo(new BigDecimal("25.0"));
        assertThat(first.getCurrencyId()).isEqualTo("RUB");

        MoexDividendDto second = result.get(1);
        assertThat(second.getRegistryCloseDate()).isEqualTo(LocalDate.of(2022, 5, 20));
        assertThat(second.getValue()).isEqualByComparingTo(new BigDecimal("18.7"));
    }

    @Test
    @DisplayName("fetchDividends — MOEX вернул пустой data: [] → результат пустой список")
    void fetchDividends_returnsEmpty_whenDataArrayEmpty() throws Exception {
        JsonNode response = MAPPER.readTree("""
                {
                  "dividends": {
                    "columns": ["secid","registryclosedate","value","currencyid"],
                    "data": []
                  }
                }
                """);
        when(api.getDividends(anyString(), anyString(), anyString())).thenReturn(response);

        List<MoexDividendDto> result = client.fetchDividends("SBER");

        assertThat(result).isEmpty();
    }
}
