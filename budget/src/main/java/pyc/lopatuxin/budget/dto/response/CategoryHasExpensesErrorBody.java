package pyc.lopatuxin.budget.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Error body returned when a category cannot be deleted because it has linked expenses.
 */
@Getter
@AllArgsConstructor
@Schema(description = "Тело ошибки при попытке удалить категорию с расходами")
public class CategoryHasExpensesErrorBody {

    @Schema(description = "Код ошибки", example = "CATEGORY_HAS_EXPENSES")
    private final String code;

    @Schema(description = "Количество связанных расходов")
    private final int expenseCount;
}
