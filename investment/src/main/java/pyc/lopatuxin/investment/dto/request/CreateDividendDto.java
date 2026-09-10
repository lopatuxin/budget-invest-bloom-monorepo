package pyc.lopatuxin.investment.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateDividendDto {

    @NotBlank
    @Size(max = 20)
    private String ticker;

    @NotNull
    private LocalDate recordDate;

    private LocalDate paymentDate;

    @NotNull
    @Positive
    @Digits(integer = 11, fraction = 4)
    private BigDecimal amountPerShare;

    // The column is varchar(3), so an over-long code has to be rejected at the boundary:
    // reaching the insert would surface as the duplicate-record 409 instead of a validation error.
    @Size(min = 3, max = 3)
    private String currency;
}
