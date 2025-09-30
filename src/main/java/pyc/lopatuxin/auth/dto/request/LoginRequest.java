package pyc.lopatuxin.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Данные для аутентификации пользователя")
public class LoginRequest {

    @Schema(description = "Email адрес пользователя", example = "user@example.com")
    @NotBlank(message = "Email адрес обязателен")
    @Email(message = "Некорректный формат email адреса")
    @Size(max = 255, message = "Email адрес не может быть длиннее 255 символов")
    private String email;

    @Schema(description = "Пароль пользователя", example = "mypassword123")
    @NotBlank(message = "Пароль обязателен")
    @Size(min = 1, max = 128, message = "Пароль должен содержать от 1 до 128 символов")
    private String password;
}