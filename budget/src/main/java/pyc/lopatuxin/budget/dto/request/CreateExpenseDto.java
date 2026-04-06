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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO для создания нового расхода.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные для создания нового расхода")
public class CreateExpenseDto {

    /**
     * Идентификатор категории, к которой относится расход.
     */
    @NotNull(message = "Идентификатор категории обязателен")
    @Schema(description = "Идентификатор категории расхода", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID categoryId;

    /**
     * Сумма расхода в валюте пользователя.
     */
    @NotNull(message = "Сумма расхода обязательна")
    @Positive(message = "Сумма расхода должна быть положительной")
    @Digits(integer = 13, fraction = 2, message = "Сумма расхода не должна превышать 13 целых и 2 дробных знака")
    @Schema(description = "Сумма расхода (максимум 13 целых и 2 дробных знака)", example = "1500.00")
    private BigDecimal amount;

    /**
     * Текстовое описание расхода.
     */
    @Schema(description = "Описание расхода", example = "Продукты в магазине")
    private String description;

    /**
     * Дата совершения расхода.
     * Если не указана — используется текущая дата.
     */
    @Schema(description = "Дата расхода (если не указана — текущая дата)", example = "2026-04-06")
    private LocalDate date;
}
