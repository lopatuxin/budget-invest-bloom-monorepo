package pyc.lopatuxin.budget.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.budget.dto.request.InvestmentEntryRequestDto.EntryType;

import java.util.UUID;

/**
 * Request DTO for deleting an investment entry.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные для удаления инвестиционной операции")
public class DeleteInvestmentEntryRequestDto {

    @NotNull(message = "Идентификатор записи обязателен")
    @Schema(description = "Идентификатор записи (Expense или Income) для удаления",
            example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID entryId;

    @NotNull(message = "Тип операции обязателен")
    @Schema(description = "Тип операции: BUY — покупка (Expense), SELL — продажа (Income)", example = "BUY")
    private EntryType type;
}
