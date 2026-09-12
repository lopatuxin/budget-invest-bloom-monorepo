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
import pyc.lopatuxin.budget.dto.request.CategoryPageRequestDto;
import pyc.lopatuxin.budget.dto.response.CategoryPageResponseDto;
import pyc.lopatuxin.budget.service.CategoryPageService;
import pyc.lopatuxin.shared.dto.ApiRequest;
import pyc.lopatuxin.shared.dto.ResponseApi;

/**
 * Controller for the category page endpoint: the requested month's spending against the
 * category's personal norm, the last 12 months chart and the month's operations feed.
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/categories")
@RequiredArgsConstructor
@Tag(name = "Категории", description = "API для управления категориями расходов")
public class CategoryPageController {

    private final CategoryPageService categoryPageService;

    /**
     * Returns the category page for the authenticated user, the requested category and period.
     *
     * @param request request with user context, category name and period
     * @return standard response with the category page data
     */
    @PostMapping("/page")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Получить страницу категории",
            description = "Возвращает потраченное за месяц против личной нормы категории, последние 12 месяцев " +
                    "и операции категории за месяц."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Страница категории успешно получена",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Некорректные параметры запроса",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "Категория не найдена",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @CommonApiResponses
    public ResponseApi<CategoryPageResponseDto> getPage(
            @RequestBody @Valid ApiRequest<CategoryPageRequestDto> request) {

        CategoryPageResponseDto page = categoryPageService.getPage(
                request.getUser().getUserId(),
                request.getData()
        );
        return ResponseApi.success("Страница категории успешно получена", page);
    }
}
