package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class ProjectionPointDto {
    private int month;
    private LocalDate date;
    private BigDecimal value;
    private BigDecimal deposit;
    private BigDecimal withdrawal;
}
