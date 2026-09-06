package pyc.lopatuxin.budget.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.shared.port.EntryType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request DTO for creating an investment entry (buy or sell).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Данные для регистрации инвестиционной операции")
public class InvestmentEntryRequestDto {

    @NotNull(message = "Тип операции обязателен")
    @Schema(description = "Тип операции: BUY — покупка, SELL — продажа", example = "BUY")
    private EntryType type;

    @NotNull(message = "Сумма операции обязательна")
    @Positive(message = "Сумма операции должна быть положительной")
    @Schema(description = "Сумма операции", example = "10000.00")
    private BigDecimal amount;

    @NotNull(message = "Дата исполнения обязательна")
    @Schema(description = "Дата и время исполнения операции в формате ISO 8601 UTC",
            example = "2026-05-09T10:30:00Z")
    private Instant executedAt;
}
