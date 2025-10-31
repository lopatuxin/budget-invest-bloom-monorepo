package pyc.lopatuxin.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pyc.lopatuxin.auth.dto.request.ApiRequest;
import pyc.lopatuxin.auth.dto.request.LogoutRequest;
import pyc.lopatuxin.auth.dto.request.RequestHeadersDto;
import pyc.lopatuxin.auth.dto.response.LogoutResponse;
import pyc.lopatuxin.auth.dto.response.ResponseApi;
import pyc.lopatuxin.auth.service.LogoutService;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Аутентификация", description = "API для аутентификации и управления пользователями")
public class LogoutController {

    private final LogoutService logoutService;

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(
            summary = "Выход из системы",
            description = "Позволяет аутентифицированным пользователям безопасно завершить текущую сессию, " +
                    "отзывая JWT токены и очищая данные сессии. Поддерживает как выход из текущей сессии, " +
                    "так и принудительный выход со всех устройств."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Успешное завершение сессии",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Некорректные данные запроса",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "401",
            description = "Отсутствует или недействителен access token",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "403",
            description = "Токен отозван или заблокирован",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "422",
            description = "Ошибки валидации refresh token",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "500",
            description = "Внутренняя ошибка сервера при завершении сессии",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    public ResponseApi<LogoutResponse> logout(
            @Parameter(
                    description = "Данные для выхода из системы",
                    required = true,
                    schema = @Schema(implementation = ApiRequest.class)
            )
            @Valid @RequestBody ApiRequest<LogoutRequest> request,
            @Parameter(
                    description = "HTTP заголовки запроса",
                    required = true,
                    schema = @Schema(implementation = RequestHeadersDto.class)
            )
            RequestHeadersDto headers) {

        LogoutResponse logoutResponse = logoutService.logout(headers, request);

        return ResponseApi.<LogoutResponse>builder()
                .id(UUID.randomUUID())
                .status(200)
                .message(logoutResponse.getMessage())
                .timestamp(Instant.now())
                .body(logoutResponse)
                .build();
    }
}