package pyc.lopatuxin.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Единая стандартная структура запроса для всех API endpoints")
public class ApiRequest<T> {

    @Schema(description = "Информация о пользователе, выполняющем запрос")
    private UserContext user;

    @Schema(description = "Полезная нагрузка запроса с данными")
    @Valid
    private T data;
}