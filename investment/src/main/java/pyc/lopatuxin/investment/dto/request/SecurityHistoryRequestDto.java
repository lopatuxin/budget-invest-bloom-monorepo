package pyc.lopatuxin.investment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SecurityHistoryRequestDto {
    @NotBlank
    private String ticker;
    private LocalDate from;
    private LocalDate to;
}
