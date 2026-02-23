package pyc.lopatuxin.budget.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pyc.lopatuxin.budget.entity.enums.UserRole;

import java.util.UUID;

/**
 * Контекст пользователя, выполняющего запрос.
 * Заполняется API Gateway из JWT-токена и передаётся в теле каждого запроса.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Контекст пользователя, извлечённый API Gateway из JWT-токена")
public class UserContextDto {

    @NotNull(message = "userId обязателен")
    @Schema(description = "Уникальный идентификатор пользователя", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID userId;

    @NotBlank(message = "email обязателен")
    @Email(message = "Некорректный формат email")
    @Schema(description = "Email адрес пользователя", example = "user@example.com")
    private String email;

    @NotNull(message = "role обязательна")
    @Schema(description = "Роль пользователя в системе", example = "USER")
    private UserRole role;

    @Schema(description = "Идентификатор текущей сессии пользователя", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID sessionId;
}