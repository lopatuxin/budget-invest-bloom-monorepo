package pyc.lopatuxin.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ при успешной регистрации пользователя")
public class RegisterResponse {

    @Schema(description = "Уникальный идентификатор созданного пользователя", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID userId;

    @Schema(description = "Email адрес пользователя", example = "user@example.com")
    private String email;

    @Schema(description = "Имя пользователя", example = "Иван")
    private String firstName;

    @Schema(description = "Фамилия пользователя", example = "Иванов")
    private String lastName;

    @Schema(description = "Статус активности аккаунта (всегда true)", example = "true")
    private Boolean isActive;

    @Schema(description = "Статус подтверждения email (всегда false)", example = "false")
    private Boolean isVerified;

    @Schema(description = "Время создания аккаунта в ISO 8601 формате", example = "2025-08-07T12:30:45.123")
    private LocalDateTime createdAt;
}