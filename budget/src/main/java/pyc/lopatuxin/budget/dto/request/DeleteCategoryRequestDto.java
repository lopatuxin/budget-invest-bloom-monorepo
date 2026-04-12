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
 * DTO запроса на удаление категории.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные для удаления категории")
public class DeleteCategoryRequestDto {

    /**
     * Идентификатор категории для удаления.
     */
    @NotNull(message = "Идентификатор категории обязателен")
    @Schema(description = "Идентификатор категории", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID categoryId;

    /**
     * Флаг каскадного удаления: если true, удаляются все связанные расходы вместе с категорией.
     */
    @Schema(description = "Каскадное удаление связанных расходов", example = "false")
    private Boolean force;
}
