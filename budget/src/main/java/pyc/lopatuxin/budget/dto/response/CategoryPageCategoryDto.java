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
 * Категория, для которой построена страница категории.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Категория страницы категории")
public class CategoryPageCategoryDto {

    @Schema(description = "Идентификатор категории", example = "8b1f2c3d-4e5f-6789-a0b1-c2d3e4f56789")
    private UUID id;

    @Schema(description = "Название категории", example = "Продукты")
    private String name;

    @Schema(description = "Эмодзи категории (может отсутствовать)", example = "🛒")
    private String emoji;

    @Schema(description = "Признак системной категории — переименование и удаление для неё недоступны", example = "false")
    private boolean system;
}
