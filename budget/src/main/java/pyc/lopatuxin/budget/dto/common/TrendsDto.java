package pyc.lopatuxin.budget.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Тренды финансовых показателей относительно предыдущего месяца.
 * Каждое поле содержит форматированную строку вида "+8.2%" или "-3.1%".
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Тренды финансовых показателей относительно предыдущего месяца")
public class TrendsDto {

    @Schema(description = "Изменение доходов относительно предыдущего месяца", example = "+8.2%")
    private String income;

    @Schema(description = "Изменение расходов относительно предыдущего месяца", example = "-3.1%")
    private String expenses;

    @Schema(description = "Изменение баланса относительно предыдущего месяца", example = "+5.4%")
    private String balance;

    @Schema(description = "Изменение капитала относительно предыдущего месяца", example = "+1.2%")
    private String capital;

    @Schema(description = "Изменение личной инфляции относительно предыдущего месяца", example = "+0.3%")
    private String inflation;
}