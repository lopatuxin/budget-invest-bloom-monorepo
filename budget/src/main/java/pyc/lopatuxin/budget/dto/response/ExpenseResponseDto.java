package pyc.lopatuxin.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO ответа с данными о созданном расходе.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные о расходе")
public class ExpenseResponseDto {

    /**
     * Уникальный идентификатор расхода.
     */
    @Schema(description = "Идентификатор расхода", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    /**
     * Идентификатор категории, к которой относится расход.
     */
    @Schema(description = "Идентификатор категории", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID categoryId;

    /**
     * Название категории расхода.
     */
    @Schema(description = "Название категории", example = "Продукты")
    private String categoryName;

    /**
     * Сумма расхода в валюте пользователя.
     */
    @Schema(description = "Сумма расхода", example = "1500.00")
    private BigDecimal amount;

    /**
     * Текстовое описание расхода.
     */
    @Schema(description = "Описание расхода", example = "Продукты в магазине")
    private String description;

    /**
     * Дата совершения расхода.
     */
    @Schema(description = "Дата расхода", example = "2026-04-06")
    private LocalDate date;
}
