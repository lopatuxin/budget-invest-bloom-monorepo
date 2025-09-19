package pyc.lopatuxin.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pyc.lopatuxin.auth.dto.request.ApiRequest;
import pyc.lopatuxin.auth.dto.request.RegisterRequest;
import pyc.lopatuxin.auth.dto.response.ResponseApi;
import pyc.lopatuxin.auth.service.AuthService;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Аутентификация", description = "API для аутентификации и управления пользователями")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Регистрация нового пользователя",
            description = "Создает новый аккаунт пользователя в системе. После регистрации пользователю необходимо подтвердить email адрес."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Пользователь успешно зарегистрирован",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Некорректные данные в запросе или пользователь с таким email уже существует",
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
    public ResponseApi<Object> register(
            @Parameter(
                    description = "Данные для регистрации нового пользователя",
                    required = true,
                    schema = @Schema(implementation = ApiRequest.class)
            )
            @Valid @RequestBody ApiRequest<RegisterRequest> request) {

        authService.register(request.getData());
        
        return ResponseApi.builder()
                .id(UUID.randomUUID())
                .status(201)
                .message("Пользователь успешно зарегистрирован")
                .timestamp(Instant.now())
                .build();
    }
}