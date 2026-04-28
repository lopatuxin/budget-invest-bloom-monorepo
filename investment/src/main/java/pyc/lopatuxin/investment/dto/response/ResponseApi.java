package pyc.lopatuxin.investment.dto.response;

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
@Schema(description = "Единая стандартная структура ответа для всех API-эндпоинтов")
public class ResponseApi<T> {

    @Schema(description = "Уникальный идентификатор запроса (UUID) для распределённой трассировки",
            example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "HTTP статус-код ответа", example = "200")
    private Integer status;

    @Schema(description = "Человекочитаемое сообщение на русском языке", example = "Операция выполнена успешно")
    private String message;

    @Schema(description = "Временная метка формирования ответа в формате ISO 8601 UTC",
            example = "2025-08-18T14:30:45.123Z")
    private Instant timestamp;

    @Schema(description = "Полезная нагрузка ответа с данными")
    private T body;

    public static <T> ResponseApi<T> success(String message, T body) {
        return ResponseApi.<T>builder()
                .id(UUID.randomUUID())
                .status(200)
                .message(message)
                .timestamp(Instant.now())
                .body(body)
                .build();
    }

    public static <T> ResponseApi<T> created(String message, T body) {
        return ResponseApi.<T>builder()
                .id(UUID.randomUUID())
                .status(201)
                .message(message)
                .timestamp(Instant.now())
                .body(body)
                .build();
    }

    public static <T> ResponseApi<T> error(int status, String message) {
        return ResponseApi.<T>builder()
                .id(UUID.randomUUID())
                .status(status)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ResponseApi<T> error(int status, String message, T body) {
        return ResponseApi.<T>builder()
                .id(UUID.randomUUID())
                .status(status)
                .message(message)
                .timestamp(Instant.now())
                .body(body)
                .build();
    }
}
