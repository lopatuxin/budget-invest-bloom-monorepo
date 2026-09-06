package pyc.lopatuxin.budget.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pyc.lopatuxin.budget.dto.response.LifetimeBalanceResponseDto;
import pyc.lopatuxin.budget.service.BalanceService;
import pyc.lopatuxin.shared.dto.ApiRequest;
import pyc.lopatuxin.shared.dto.ResponseApi;

/**
 * Controller for balance-related endpoints.
 *
 * <p>userId is extracted from the user block populated by the API Gateway from the JWT token.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/balance")
@RequiredArgsConstructor
@Tag(name = "Баланс", description = "API для получения баланса пользователя")
public class BalanceController {

    private final BalanceService balanceService;

    /**
     * Returns the lifetime balance for the authenticated user.
     *
     * @param request request with user context (data block is not used)
     * @return standard response with lifetime balance aggregates
     */
    @PostMapping("/lifetime")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Получить баланс за всё время",
            description = "Возвращает суммарные доходы, расходы и свободный капитал пользователя за всё время."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Баланс успешно получен",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @CommonApiResponses
    public ResponseApi<LifetimeBalanceResponseDto> getLifetimeBalance(
            @RequestBody @Valid ApiRequest<Object> request) {

        LifetimeBalanceResponseDto result = balanceService.getLifetimeBalance(
                request.getUser().getUserId()
        );
        return ResponseApi.success("Баланс за всё время успешно получен", result);
    }
}
