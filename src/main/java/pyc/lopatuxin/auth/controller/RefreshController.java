package pyc.lopatuxin.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pyc.lopatuxin.auth.dto.request.RequestHeadersDto;
import pyc.lopatuxin.auth.dto.response.RefreshTokenResponse;
import pyc.lopatuxin.auth.dto.response.ResponseApi;
import pyc.lopatuxin.auth.service.RefreshTokenService;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Аутентификация", description = "API для аутентификации и управления пользователями")
public class RefreshController {

    private final RefreshTokenService refreshTokenService;

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Обновление JWT токенов",
            description = "Обновляет access и refresh токены используя действующий refresh token. Реализует Refresh Token Rotation."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Токены успешно обновлены",
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
            description = "Недействительный или истекший refresh token",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "403",
            description = "Refresh token отозван, использован или аккаунт заблокирован",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "Пользователь или сессия не найдены",
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
            responseCode = "429",
            description = "Превышен лимит операций refresh",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "500",
            description = "Внутренняя ошибка сервера",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    public ResponseApi<RefreshTokenResponse> refresh(
            @Parameter(
                    description = "HTTP заголовки запроса",
                    required = true,
                    schema = @Schema(implementation = RequestHeadersDto.class)
            )
            RequestHeadersDto headers,
            @CookieValue(name = "refreshToken") String refreshTokenFromCookie,
            HttpServletResponse httpResponse) {

        log.info("Refresh token: {}", refreshTokenFromCookie);
        RefreshTokenResponse refreshTokenResponse = refreshTokenService.refreshTokens(
                refreshTokenFromCookie,
                headers,
                httpResponse
        );

        return ResponseApi.<RefreshTokenResponse>builder()
                .id(UUID.randomUUID())
                .status(200)
                .message("Токены успешно обновлены")
                .timestamp(Instant.now())
                .body(refreshTokenResponse)
                .build();
    }
}