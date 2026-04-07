package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO ответа с данными о созданной категории.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные созданной категории")
public class CategoryResponseDto {

    /**
     * Уникальный идентификатор категории.
     */
    @Schema(description = "Идентификатор категории")
    private UUID id;

    /**
     * Название категории.
     */
    @Schema(description = "Название категории")
    private String name;

    /**
     * Лимит бюджета категории.
     */
    @Schema(description = "Лимит бюджета")
    private BigDecimal budget;

    /**
     * Эмодзи-иконка категории.
     */
    @Schema(description = "Эмодзи-иконка")
    private String emoji;
}
