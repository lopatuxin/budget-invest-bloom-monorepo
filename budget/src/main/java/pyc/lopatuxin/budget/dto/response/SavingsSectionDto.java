package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Норма сбережений: за окно из 12 месяцев и за текущий (неполный) месяц.
 * Оба поля равны null при нулевых доходах за соответствующий период.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Норма сбережений пользователя")
public class SavingsSectionDto {

    @Schema(description = "Норма сбережений за 12 месяцев в процентах, зажата в [-99, 99]", example = "33")
    private Integer rate12m;

    @Schema(description = "Норма сбережений за текущий месяц в процентах, зажата в [-99, 99]", example = "49")
    private Integer currentMonthRate;
}
