package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;
import pyc.lopatuxin.budget.entity.enums.OperationKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Одна операция (расход или доход) в ленте операций за месяц.
 * Поля, не относящиеся к виду операции ({@code kind}), отсутствуют в JSON.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Операция в ленте за месяц (расход или доход)")
public class OperationDto {

    @Schema(description = "Идентификатор операции (расхода или дохода)")
    private UUID id;

    @Schema(description = "Вид операции")
    private OperationKind kind;

    @Schema(description = "Дата операции", example = "2026-09-18")
    private LocalDate date;

    @Schema(description = "Сумма операции", example = "2340.00")
    private BigDecimal amount;

    @Schema(description = "Описание операции")
    private String description;

    @Schema(description = "Идентификатор категории (только для расхода)")
    private UUID categoryId;

    @Schema(description = "Название категории (только для расхода)")
    private String categoryName;

    @Schema(description = "Эмодзи категории (только для расхода)")
    private String categoryEmoji;

    @Schema(description = "Код источника дохода (только для дохода)")
    private IncomeSource source;

    @Schema(description = "Читаемое название источника дохода (только для дохода)")
    private String sourceName;
}
