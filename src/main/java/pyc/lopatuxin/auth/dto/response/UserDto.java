package pyc.lopatuxin.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Данные пользователя в ответе")
public class UserDto {

    @Schema(description = "Уникальный идентификатор пользователя", example = "123e4567-e89b-12d3-a456-426614174000")
    private String userId;

    @Schema(description = "Email адрес пользователя", example = "user@example.com")
    private String email;

    @Schema(description = "Статус активности аккаунта", example = "true")
    private Boolean isActive;

    @Schema(description = "Статус подтверждения email", example = "true")
    private Boolean isVerified;

    @Schema(description = "Список ролей пользователя", example = "[\"USER\"]")
    private List<String> roles;

    @Schema(description = "Время последнего входа в ISO 8601 формате", example = "2025-08-07T12:30:45.123Z")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime lastLoginAt;
}