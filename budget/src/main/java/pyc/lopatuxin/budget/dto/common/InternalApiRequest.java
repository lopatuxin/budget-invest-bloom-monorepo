package pyc.lopatuxin.budget.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request wrapper for internal service-to-service endpoints.
 * Uses {@link InternalUserContextDto} which requires only userId —
 * email and role are not available when investment service calls budget directly.
 *
 * @param <T> request payload type
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Структура запроса для внутренних вызовов сервис-к-сервису")
public class InternalApiRequest<T> {

    @Valid
    @NotNull(message = "Блок user обязателен")
    @Schema(description = "Облегчённый контекст пользователя (только userId)")
    private InternalUserContextDto user;

    @Valid
    @NotNull(message = "Блок data обязателен")
    @Schema(description = "Полезная нагрузка запроса с данными")
    private T data;
}
