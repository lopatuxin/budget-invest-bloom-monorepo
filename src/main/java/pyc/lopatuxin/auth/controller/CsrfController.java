package pyc.lopatuxin.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pyc.lopatuxin.auth.dto.response.ResponseApi;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "CSRF", description = "API для работы с CSRF токенами")
public class CsrfController {

    @GetMapping("/csrf")
    @Operation(
            summary = "Получение CSRF токена",
            description = "Возвращает CSRF токен для защиты от атак межсайтовой подделки запросов"
    )
    @ApiResponse(
            responseCode = "200",
            description = "CSRF токен успешно получен",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    public ResponseApi<String> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

        return ResponseApi.<String>builder()
                .id(UUID.randomUUID())
                .status(200)
                .message("CSRF токен успешно получен")
                .timestamp(Instant.now())
                .body(csrfToken.getToken())
                .build();
    }
}