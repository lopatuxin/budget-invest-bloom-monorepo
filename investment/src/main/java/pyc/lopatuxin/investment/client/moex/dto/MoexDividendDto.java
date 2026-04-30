package pyc.lopatuxin.investment.client.moex.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MoexDividendDto {

    @JsonProperty("secid")
    private String secid;

    @JsonProperty("registryclosedate")
    private LocalDate registryCloseDate;

    @JsonProperty("value")
    private BigDecimal value;

    @JsonProperty("currencyid")
    private String currencyId;
}
