package pyc.lopatuxin.budget.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * DTO запроса на удаление дохода.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные для удаления дохода")
public class DeleteIncomeRequestDto {

    /**
     * Идентификатор дохода для удаления.
     */
    @NotNull(message = "Идентификатор дохода обязателен")
    @Schema(description = "Идентификатор дохода", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID incomeId;
}
