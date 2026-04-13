package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO ответа с детальной аналитикой по категории расходов.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Детальная аналитика по категории расходов")
public class CategoryAnalyticsResponseDto {

    /**
     * Уникальный идентификатор категории.
     */
    @Schema(description = "Идентификатор категории", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID categoryId;

    /**
     * Название категории.
     */
    @Schema(description = "Название категории", example = "Еда")
    private String categoryName;

    /**
     * Эмодзи-иконка категории.
     */
    @Schema(description = "Эмодзи-иконка категории")
    private String emoji;

    /**
     * Лимит бюджета категории.
     */
    @Schema(description = "Лимит бюджета категории", example = "15000.00")
    private BigDecimal budget;

    /**
     * Помесячные данные расходов за выбранный год (12 записей).
     */
    @Schema(description = "Помесячные расходы за выбранный год (12 записей)")
    private List<MonthlyMetricDto> monthlyData;

    /**
     * Годовые данные расходов за все доступные годы.
     */
    @Schema(description = "Годовые суммы расходов за все годы")
    private List<YearlyMetricDto> yearlyData;

    /**
     * Список расходов за выбранный период.
     */
    @Schema(description = "Список расходов за выбранный период")
    private List<ExpenseResponseDto> expenses;

    /**
     * Общая сумма расходов за выбранный период.
     */
    @Schema(description = "Общая сумма расходов за выбранный период", example = "45000.00")
    private BigDecimal totalExpenses;

    /**
     * Total expenses for the selected year.
     */
    @Schema(description = "Общая сумма расходов за выбранный год", example = "120000.00")
    private BigDecimal totalYear;

    /**
     * Average monthly expenses for the selected year (only months with data are counted).
     */
    @Schema(description = "Среднемесячные расходы за выбранный год (только месяцы с данными)", example = "10000.00")
    private BigDecimal averageYear;
}
