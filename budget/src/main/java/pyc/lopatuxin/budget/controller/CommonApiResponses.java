package pyc.lopatuxin.budget.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import pyc.lopatuxin.shared.dto.ResponseApi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The 401/500 {@code @ApiResponse} pair every budget endpoint documents identically.
 * Endpoint-specific responses (2xx, 400, 404, 409) stay declared on the method itself.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "401",
        description = "Не авторизован",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseApi.class))
)
@ApiResponse(
        responseCode = "500",
        description = "Внутренняя ошибка сервера",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseApi.class))
)
public @interface CommonApiResponses {
}
