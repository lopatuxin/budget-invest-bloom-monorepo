package pyc.lopatuxin.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ на успешный выход из системы")
public class LogoutResponse {

    @Schema(description = "Сообщение об успешном завершении сессии", example = "Вы успешно вышли из системы")
    private String message;

    @Schema(description = "Количество завершенных сессий", example = "1")
    private Integer loggedOut;

    @Schema(description = "Время завершения сессии в ISO 8601 формате", example = "2025-08-07T12:30:45.123Z")
    private Instant timestamp;
}