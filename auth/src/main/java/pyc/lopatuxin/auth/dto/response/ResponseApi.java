package pyc.lopatuxin.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Единая стандартная структура ответа для всех API endpoints")
public class ResponseApi<T> {

    @Schema(description = "Уникальный идентификатор запроса в формате UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "HTTP статус код ответа", example = "200")
    private Integer status;

    @Schema(description = "Человекочитаемое сообщение на русском языке", example = "Операция выполнена успешно")
    private String message;

    @Schema(description = "Временная метка формирования ответа в ISO 8601 UTC формате", example = "2025-08-18T14:30:45.123Z")
    private Instant timestamp;

    @Schema(description = "Полезная нагрузка ответа с данными")
    private T body;

    /**
     * Создает стандартный ответ об ошибке с HTTP статусом 400 (Bad Request)
     *
     * @param <T> тип данных в теле ответа
     * @param message сообщение об ошибке
     * @return объект ResponseApi с информацией об ошибке
     */
    public static <T> ResponseApi<T> error(String message) {
        return ResponseApi.<T>builder()
                .id(UUID.randomUUID())
                .status(400)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Создает стандартный ответ об ошибке с HTTP статусом 400 (Bad Request) и дополнительными данными
     *
     * @param <T> тип данных в теле ответа
     * @param message сообщение об ошибке
     * @param body дополнительные данные об ошибке (например, детали валидации)
     * @return объект ResponseApi с информацией об ошибке и дополнительными данными
     */
    public static <T> ResponseApi<T> error(String message, T body) {
        return ResponseApi.<T>builder()
                .id(UUID.randomUUID())
                .status(400)
                .message(message)
                .timestamp(Instant.now())
                .body(body)
                .build();
    }

    /**
     * Создает стандартный ответ об ошибке с указанным HTTP статусом
     *
     * @param <T> тип данных в теле ответа
     * @param status HTTP статус код
     * @param message сообщение об ошибке
     * @return объект ResponseApi с информацией об ошибке
     */
    public static <T> ResponseApi<T> error(int status, String message) {
        return ResponseApi.<T>builder()
                .id(UUID.randomUUID())
                .status(status)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}