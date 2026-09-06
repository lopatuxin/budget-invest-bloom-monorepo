package pyc.lopatuxin.budget.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.budget.entity.enums.NormStatus;

import java.math.BigDecimal;

/**
 * Изменение показателя в процентах относительно базового значения (год назад,
 * предыдущие 12 месяцев), со статусом по личным порогам. При статусе
 * {@link NormStatus#NO_HISTORY} percent равен null.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Изменение показателя в процентах со статусом по личным порогам")
public class ChangeDto {

    @Schema(description = "Изменение в процентах, округлено до 1 знака", example = "24.2")
    private BigDecimal percent;

    @Schema(description = "Статус изменения")
    private NormStatus status;
}
