package pyc.lopatuxin.budget.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Lightweight user context for internal service-to-service calls.
 * Contains only userId — email and role are not available in internal requests
 * because the investment service calls budget directly without going through the gateway.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Облегчённый контекст пользователя для внутренних вызовов сервис-к-сервису")
public class InternalUserContextDto {

    @NotNull(message = "userId обязателен")
    @Schema(description = "Уникальный идентификатор пользователя", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID userId;
}
