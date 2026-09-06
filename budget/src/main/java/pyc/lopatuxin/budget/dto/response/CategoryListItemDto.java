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
 * Урезанные данные категории для формы новой операции (без лимита бюджета).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Категория расходов для формы операции")
public class CategoryListItemDto {

    @Schema(description = "Идентификатор категории")
    private UUID id;

    @Schema(description = "Название категории")
    private String name;

    @Schema(description = "Эмодзи-иконка категории")
    private String emoji;
}
