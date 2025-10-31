package pyc.lopatuxin.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pyc.lopatuxin.auth.config.resolver.RequestHeadersResolver;

/**
 * DTO для HTTP заголовков запроса.
 * Автоматически заполняется из заголовков через {@link RequestHeadersResolver}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "HTTP заголовки запроса")
public class RequestHeadersDto {

    @Schema(description = "JWT токен в формате Bearer", example = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String authorization;

    @Schema(description = "JWT токен без префикса Bearer", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String jwt;

    @Schema(description = "Refresh токен", example = "550e8400-e29b-41d4-a716-446655440000")
    private String refreshToken;

    @Schema(description = "User-Agent клиента", example = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
    private String userAgent;

    @Schema(description = "IP адрес клиента (из X-Forwarded-For или X-Real-IP)", example = "192.168.1.1")
    private String xForwardedFor;

    /**
     * Извлекает IP адрес клиента из заголовка X-Forwarded-For.
     * Если заголовок содержит несколько IP адресов, возвращает первый.
     *
     * @return IP адрес клиента или null, если заголовок отсутствует
     */
    public String extractIpAddress() {
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return null;
    }
}