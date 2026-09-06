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
import pyc.lopatuxin.budget.dto.common.PeriodDto;
import pyc.lopatuxin.budget.dto.response.OperationsResponseDto;
import pyc.lopatuxin.budget.service.OperationService;
import pyc.lopatuxin.shared.dto.ApiRequest;
import pyc.lopatuxin.shared.dto.ResponseApi;

/**
 * Контроллер для получения ленты операций (расходов и доходов) пользователя за месяц.
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/operations")
@RequiredArgsConstructor
@Tag(name = "Операции", description = "API для получения ленты операций за месяц")
public class OperationController {

    private final OperationService operationService;

    /**
     * Возвращает ленту не-трансферных операций (расходов и доходов) пользователя за указанный месяц.
     *
     * @param request запрос с контекстом пользователя и периодом (месяц, год)
     * @return стандартный ответ с лентой операций
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Получить ленту операций за месяц",
            description = "Возвращает все не-трансферные расходы и доходы пользователя за указанный месяц " +
                    "одним списком, отсортированным по дате и времени создания по убыванию."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Лента операций успешно получена",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Некорректные параметры запроса (month вне диапазона 1-12, year вне диапазона 2020-2100)",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @CommonApiResponses
    public ResponseApi<OperationsResponseDto> getOperations(
            @RequestBody @Valid ApiRequest<PeriodDto> request) {

        OperationsResponseDto result = operationService.getOperations(
                request.getUser().getUserId(),
                request.getData().getMonth(),
                request.getData().getYear()
        );
        return ResponseApi.success("Лента операций успешно получена", result);
    }
}
