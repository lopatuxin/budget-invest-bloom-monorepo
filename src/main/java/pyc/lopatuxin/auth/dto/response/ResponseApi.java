package pyc.lopatuxin.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Единая стандартная структура ответа для всех API endpoints")
public class ResponseApi<T> {

    @Schema(description = "Уникальный идентификатор запроса в формате UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String id;

    @Schema(description = "HTTP статус код ответа", example = "200")
    private Integer status;

    @Schema(description = "Человекочитаемое сообщение на русском языке", example = "Операция выполнена успешно")
    private String message;

    @Schema(description = "Временная метка формирования ответа в ISO 8601 UTC формате", example = "2025-08-18T14:30:45.123Z")
    private String timestamp;

    @Schema(description = "Полезная нагрузка ответа с данными")
    private T body;
}