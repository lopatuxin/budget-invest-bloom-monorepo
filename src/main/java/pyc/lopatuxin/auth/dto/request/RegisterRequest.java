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
@Schema(description = "Данные для регистрации нового пользователя")
public class RegisterRequest {

    @Schema(description = "Email адрес пользователя (будет логином)", example = "user@example.com")
    @NotBlank(message = "Email адрес обязателен")
    @Email(message = "Некорректный формат email адреса")
    @Size(max = 255, message = "Email адрес не может быть длиннее 255 символов")
    private String email;

    @Schema(description = "Пароль для аккаунта (минимум 6 символов)", example = "mypassword123")
    @NotBlank(message = "Пароль обязателен")
    @Size(min = 6, max = 128, message = "Пароль должен содержать от 6 до 128 символов")
    private String password;

    @Schema(description = "Имя пользователя", example = "Иван")
    @NotBlank(message = "Имя пользователя обязательно")
    @Size(max = 50, message = "Имя не может быть длиннее 50 символов")
    private String firstName;

    @Schema(description = "Фамилия пользователя", example = "Иванов")
    @NotBlank(message = "Фамилия пользователя обязательна")
    @Size(max = 50, message = "Фамилия не может быть длиннее 50 символов")
    private String lastName;
}