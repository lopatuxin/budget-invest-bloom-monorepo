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
import pyc.lopatuxin.budget.dto.response.OverviewSummaryResponseDto;
import pyc.lopatuxin.budget.dto.response.ResponseApi;
import pyc.lopatuxin.budget.service.OverviewSummaryService;

/**
 * Controller for the index page overview endpoint.
 *
 * <p>Accepts a POST request with unified API contract: userId is extracted from
 * the user block populated by the API Gateway from the JWT token.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/overview")
@RequiredArgsConstructor
@Tag(name = "Обзор", description = "API для страницы обзора финансов пользователя")
public class OverviewSummaryController {

    private final OverviewSummaryService overviewSummaryService;

    /**
     * Returns an aggregated overview for the index page for the given month and year.
     *
     * @param request request with user context and period (month, year)
     * @return standard response with overview summary
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Получить сводку обзора",
            description = "Возвращает агрегированную сводку доходов, расходов, баланса и капитала " +
                    "за указанный период, топ-4 категории расходов и тренды капитала, расходов и баланса."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Сводка обзора успешно получена",
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
    public ResponseApi<OverviewSummaryResponseDto> getOverview(
            @RequestBody @Valid ApiRequest<PeriodDto> request) {

        OverviewSummaryResponseDto overview = overviewSummaryService.getOverview(
                request.getUser().getUserId(),
                request.getData().getMonth(),
                request.getData().getYear()
        );
        return ResponseApi.success("Сводка обзора успешно получена", overview);
    }
}
