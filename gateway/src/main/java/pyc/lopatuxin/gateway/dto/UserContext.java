package pyc.lopatuxin.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Контекст аутентифицированного пользователя, извлекаемый из JWT-токена.
 * <p>
 * Передаётся внутри Gateway и вшивается в тело запроса при обогащении
 * перед отправкой в downstream-сервисы (например, Budget).
 * Поля соответствуют claims, записанным в JWT Auth-сервисом.
 * </p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Контекст аутентифицированного пользователя, извлечённый из JWT")
public class UserContext {

    /**
     * Уникальный идентификатор пользователя в системе.
     * Извлекается из claim {@code sub} JWT-токена.
     */
    @Schema(
            description = "Уникальный идентификатор пользователя",
            example = "123e4567-e89b-12d3-a456-426614174000"
    )
    private UUID userId;

    /**
     * Адрес электронной почты пользователя.
     * Извлекается из claim {@code email} JWT-токена.
     */
    @Schema(
            description = "Адрес электронной почты пользователя",
            example = "user@example.com"
    )
    private String email;

    /**
     * Роль пользователя в системе.
     * Извлекается из claim {@code role} JWT-токена.
     * Возможные значения: {@code USER}, {@code ADMIN}, {@code MODERATOR}.
     */
    @Schema(
            description = "Роль пользователя в системе",
            example = "USER",
            allowableValues = {"USER", "ADMIN", "MODERATOR"}
    )
    private String role;

    /**
     * Идентификатор сессии пользователя.
     * Извлекается из claim {@code sessionId} JWT-токена.
     * Используется для трассировки и аудита запросов.
     */
    @Schema(
            description = "Идентификатор текущей сессии пользователя",
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    private UUID sessionId;
}
