package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные о доходе")
public class IncomeResponseDto {

    @Schema(description = "ID дохода")
    private UUID id;

    @Schema(description = "Код источника", example = "SALARY")
    private IncomeSource source;

    @Schema(description = "Читаемое название источника", example = "Зарплата")
    private String sourceName;

    @Schema(example = "50000.00")
    private BigDecimal amount;

    private String description;

    @Schema(example = "2026-04-13")
    private LocalDate date;
}
