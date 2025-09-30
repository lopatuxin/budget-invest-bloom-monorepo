package pyc.lopatuxin.auth.dto.response;

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
@Schema(description = "Ответ на успешную аутентификацию пользователя")
public class LoginResponse {

    @Schema(description = "JWT токен для авторизации API запросов", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "JWT токен для обновления access токена", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;

    @Schema(description = "Тип токена", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Время жизни access токена в секундах", example = "900")
    private Integer expiresIn;

    @Schema(description = "Информация о пользователе")
    private UserDto user;
}