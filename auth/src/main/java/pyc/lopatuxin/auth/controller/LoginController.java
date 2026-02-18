package pyc.lopatuxin.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pyc.lopatuxin.auth.dto.request.ApiRequest;
import pyc.lopatuxin.auth.dto.request.LoginRequest;
import pyc.lopatuxin.auth.dto.request.RequestHeadersDto;
import pyc.lopatuxin.auth.dto.response.LoginResponse;
import pyc.lopatuxin.auth.dto.response.ResponseApi;
import pyc.lopatuxin.auth.service.LoginService;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Аутентификация", description = "API для аутентификации и управления пользователями")
public class LoginController {

    private final LoginService loginService;

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Аутентификация пользователя",
            description = "Выполняет аутентификацию пользователя по email и паролю. Возвращает JWT токены для авторизации."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Аутентификация прошла успешно",
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
            description = "Неверные учетные данные",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "403",
            description = "Аккаунт заблокирован или неактивен",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "422",
            description = "Ошибки валидации входных данных",
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
    public ResponseApi<LoginResponse> login(
            @Parameter(
                    description = "Данные для аутентификации пользователя",
                    required = true,
                    schema = @Schema(implementation = ApiRequest.class)
            )
            @Valid @RequestBody ApiRequest<LoginRequest> request,
            @Parameter(
                    description = "HTTP заголовки запроса",
                    required = true,
                    schema = @Schema(implementation = RequestHeadersDto.class)
            )
            RequestHeadersDto headers,
            HttpServletResponse httpResponse) {

        LoginResponse loginResponse = loginService.login(
                request.getData(),
                headers,
                httpResponse
        );

        return ResponseApi.<LoginResponse>builder()
                .id(UUID.randomUUID())
                .status(200)
                .message("Аутентификация прошла успешно")
                .timestamp(Instant.now())
                .body(loginResponse)
                .build();
    }
}