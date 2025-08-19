package pyc.lopatuxin.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Контекст пользователя для аутентификации")
public class UserContext {

    @Schema(description = "Email адрес пользователя", example = "user@example.com")
    @NotBlank(message = "Email пользователя обязателен")
    @Email(message = "Некорректный формат email")
    private String email;

    @Schema(description = "Пароль пользователя")
    @NotBlank(message = "Пароль обязателен")
    private String password;
}