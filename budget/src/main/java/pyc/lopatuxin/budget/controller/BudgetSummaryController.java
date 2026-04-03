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
import pyc.lopatuxin.budget.dto.common.ApiRequest;
import pyc.lopatuxin.budget.dto.common.PeriodDto;
import pyc.lopatuxin.budget.dto.response.BudgetSummaryResponseDto;
import pyc.lopatuxin.budget.dto.response.ResponseApi;
import pyc.lopatuxin.budget.service.BudgetSummaryService;

/**
 * Контроллер для получения агрегированной сводки бюджета пользователя.
 *
 * <p>Принимает POST-запрос с unified API contract: userId извлекается из блока user,
 * заполненного API Gateway из JWT-токена.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/summary")
@RequiredArgsConstructor
@Tag(name = "Бюджет", description = "API для управления бюджетом пользователя")
public class BudgetSummaryController {

    private final BudgetSummaryService budgetSummaryService;

    /**
     * Возвращает агрегированную сводку бюджета пользователя за указанный месяц и год.
     *
     * @param request запрос с контекстом пользователя и периодом (месяц, год)
     * @return стандартный ответ со сводкой бюджета
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Получить сводку бюджета",
            description = "Возвращает агрегированную сводку доходов, расходов, баланса, капитала и личной инфляции " +
                    "за указанный период, а также список категорий с расходами и процентом использования лимита."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Сводка бюджета успешно получена",
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
    @ApiResponse(
            responseCode = "401",
            description = "Не авторизован",
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
    public ResponseApi<BudgetSummaryResponseDto> getSummary(
            @RequestBody @Valid ApiRequest<PeriodDto> request) {

        BudgetSummaryResponseDto summary = budgetSummaryService.getSummary(
                request.getUser().getUserId(),
                request.getData().getMonth(),
                request.getData().getYear()
        );
        return ResponseApi.success("Сводка бюджета успешно получена", summary);
    }
}
