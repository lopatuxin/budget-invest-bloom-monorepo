package pyc.lopatuxin.budget.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные для создания дохода")
public class CreateIncomeDto {

    @NotNull(message = "Источник дохода обязателен")
    @Schema(description = "Источник дохода", example = "SALARY")
    private IncomeSource source;

    @NotNull(message = "Сумма дохода обязательна")
    @Positive(message = "Сумма должна быть положительной")
    @Digits(integer = 13, fraction = 2, message = "Некорректный формат суммы")
    @Schema(example = "50000.00")
    private BigDecimal amount;

    @Schema(description = "Описание (необязательно)")
    private String description;

    @Schema(description = "Дата дохода. Если не указана — используется текущая дата", example = "2026-04-13")
    private LocalDate date;
}
