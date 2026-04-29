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
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.entity.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransactionDto {

    @NotBlank
    @Size(max = 20)
    private String ticker;

    @NotNull
    private TransactionType type;

    @NotNull
    private SecurityType securityType;

    @NotNull
    @Positive
    @Digits(integer = 11, fraction = 8)
    private BigDecimal quantity;

    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 2)
    private BigDecimal price;

    @NotNull
    private Instant executedAt;
}
