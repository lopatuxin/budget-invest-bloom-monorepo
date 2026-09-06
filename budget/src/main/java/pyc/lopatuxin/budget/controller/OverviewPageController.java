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
import pyc.lopatuxin.budget.dto.response.OverviewPageResponseDto;
import pyc.lopatuxin.budget.service.OverviewPageService;
import pyc.lopatuxin.shared.dto.ApiRequest;
import pyc.lopatuxin.shared.dto.ResponseApi;

/**
 * Controller for the overview page ("Capital") endpoint.
 *
 * <p>Accepts a POST request with unified API contract: userId is extracted from
 * the user block populated by the API Gateway from the JWT token. The page always shows
 * "now", so the request carries no parameters.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/overview")
@RequiredArgsConstructor
@Tag(name = "Обзор", description = "API для страницы обзора («Капитал»)")
public class OverviewPageController {

    private final OverviewPageService overviewPageService;

    /**
     * Returns the overview page for the authenticated user.
     *
     * @param request request with user context (data block is not used)
     * @return standard response with the overview page data
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Получить страницу обзора",
            description = "Возвращает капитал с траекторией за 12 месяцев, четыре плитки, доходы по месяцам " +
                    "и итоги за 12 месяцев против предыдущих 12."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Страница обзора успешно получена",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @CommonApiResponses
    public ResponseApi<OverviewPageResponseDto> getOverview(
            @RequestBody @Valid ApiRequest<Object> request) {

        OverviewPageResponseDto overview = overviewPageService.getOverview(request.getUser().getUserId());
        return ResponseApi.success("Страница обзора успешно получена", overview);
    }
}
