package pyc.lopatuxin.investment.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DateRangeDto {
    private LocalDate from;
    private LocalDate to;
}
