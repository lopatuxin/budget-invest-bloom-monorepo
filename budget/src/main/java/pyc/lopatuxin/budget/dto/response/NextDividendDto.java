package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ближайшая предстоящая выплата дивидендов по портфелю.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Ближайшая предстоящая выплата дивидендов")
public class NextDividendDto {

    @Schema(description = "Тикер бумаги", example = "LKOH")
    private String ticker;

    @Schema(description = "Название бумаги", example = "ЛУКОЙЛ")
    private String securityName;

    @Schema(description = "Дата выплаты", example = "2026-10-03")
    private LocalDate paymentDate;

    @Schema(description = "Сумма выплаты", example = "4800.00")
    private BigDecimal totalAmount;
}
