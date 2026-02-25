package pyc.lopatuxin.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Обогащённый запрос, формируемый Gateway перед отправкой в downstream-сервисы.
 * <p>
 * Содержит пользовательский контекст, извлечённый из JWT, и исходную полезную нагрузку
 * запроса. Соответствует единому API-контракту платформы: поле {@code user} заполняется
 * Gateway автоматически, поле {@code data} передаётся из тела оригинального запроса клиента.
 * </p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Обогащённый запрос с пользовательским контекстом для downstream-сервисов")
public class EnrichedRequest {

    /**
     * Контекст аутентифицированного пользователя.
     * Заполняется Gateway на основе данных из JWT-токена.
     * Downstream-сервисы доверяют этому полю без самостоятельной валидации токена.
     */
    @Schema(description = "Контекст пользователя, извлечённый из JWT Gateway-ем")
    private UserContext user;

    /**
     * Полезная нагрузка запроса с данными эндпоинта.
     * Соответствует полю {@code data} из тела оригинального запроса клиента.
     * При отсутствии поля {@code data} в запросе — используется всё тело целиком.
     */
    @Schema(description = "Полезная нагрузка запроса, специфичная для конкретного эндпоинта")
    private Object data;
}
