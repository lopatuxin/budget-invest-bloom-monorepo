package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Response DTO returned after creating an investment entry.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Результат регистрации инвестиционной операции")
public class InvestmentEntryResponseDto {

    @Schema(description = "Идентификатор созданной записи (Expense или Income)",
            example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID entryId;
}
