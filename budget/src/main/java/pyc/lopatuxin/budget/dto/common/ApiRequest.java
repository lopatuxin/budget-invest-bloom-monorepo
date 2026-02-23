package pyc.lopatuxin.budget.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Универсальная обёртка запроса для всех API-эндпоинтов budget-сервиса.
 * Блок user заполняется API Gateway из JWT-токена.
 *
 * @param <T> тип полезной нагрузки запроса
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Единая стандартная структура запроса для всех API-эндпоинтов")
public class ApiRequest<T> {

    @Schema(description = "Контекст пользователя, выполняющего запрос (заполняется API Gateway)")
    private UserContextDto user;

    @Valid
    @Schema(description = "Полезная нагрузка запроса с данными")
    private T data;
}