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
 * Сводка расходов по одной категории за запрошенный месяц.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Сводка расходов по категории за запрошенный период")
public class CategorySummaryDto {

    @Schema(description = "Уникальный идентификатор категории", example = "a1b2c3d4-e5f6-7890-ab12-cd34ef567890")
    private UUID id;

    @Schema(description = "Название категории", example = "Продукты")
    private String name;

    @Schema(description = "Эмодзи категории (может отсутствовать)", example = "🛒")
    private String emoji;

    @Schema(description = "Фактически потраченная сумма по категории за месяц", example = "25000.00")
    private BigDecimal amount;

    @Schema(description = "Установленный бюджетный лимит категории", example = "30000.00")
    private BigDecimal budget;

    @Schema(description = "Процент использования лимита (0-100), округлённый до двух знаков", example = "83.33")
    private BigDecimal percentUsed;
}