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
import pyc.lopatuxin.budget.dto.common.YearDto;
import pyc.lopatuxin.budget.dto.response.AnalyticsPageResponseDto;
import pyc.lopatuxin.budget.service.AnalyticsPageService;
import pyc.lopatuxin.shared.dto.ApiRequest;
import pyc.lopatuxin.shared.dto.ResponseApi;

/**
 * Controller for the analytics page ("Year to year") endpoint.
 *
 * <p>Accepts a POST request with unified API contract: userId is extracted from the user block
 * populated by the API Gateway from the JWT token, year comes from the request body.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/analytics")
@RequiredArgsConstructor
@Tag(name = "Аналитика", description = "API для страницы аналитики («Год к году»)")
public class AnalyticsPageController {

    private final AnalyticsPageService analyticsPageService;

    /**
     * Returns the analytics page for the authenticated user and the requested year.
     *
     * @param request request with user context and the requested year
     * @return standard response with the analytics page data
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Получить страницу аналитики",
            description = "Возвращает три раздела (расходы, доходы, сбережения) за год Y против предыдущего года P " +
                    "с помесячными столбцами, а также разбивку категорий расходов по вкладу в личную инфляцию."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Страница аналитики успешно получена",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Некорректные параметры запроса (year вне диапазона 1950-2100)",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @CommonApiResponses
    public ResponseApi<AnalyticsPageResponseDto> getAnalytics(
            @RequestBody @Valid ApiRequest<YearDto> request) {

        AnalyticsPageResponseDto analytics = analyticsPageService.getAnalytics(
                request.getUser().getUserId(),
                request.getData().getYear()
        );
        return ResponseApi.success("Страница аналитики успешно получена", analytics);
    }
}
