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
import pyc.lopatuxin.budget.dto.request.CategoryAnalyticsRequestDto;
import pyc.lopatuxin.budget.dto.response.CategoryAnalyticsResponseDto;
import pyc.lopatuxin.budget.dto.response.ResponseApi;
import pyc.lopatuxin.budget.service.CategoryAnalyticsService;

/**
 * Контроллер для получения детальной аналитики по категории расходов.
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/categories")
@RequiredArgsConstructor
@Tag(name = "Аналитика категорий", description = "API для получения детальной аналитики по категориям расходов")
public class CategoryAnalyticsController {

    private final CategoryAnalyticsService categoryAnalyticsService;

    /**
     * Возвращает детальную аналитику по категории расходов пользователя.
     *
     * @param request запрос с контекстом пользователя и параметрами аналитики
     * @return стандартный ответ с аналитикой категории
     */
    @PostMapping("/analytics")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Получить аналитику категории",
            description = "Возвращает помесячные и годовые данные расходов, список расходов за период " +
                    "и общую сумму по указанной категории"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Аналитика категории успешно получена",
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
            responseCode = "401",
            description = "Не авторизован",
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
    @ApiResponse(
            responseCode = "500",
            description = "Внутренняя ошибка сервера",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    public ResponseApi<CategoryAnalyticsResponseDto> getAnalytics(
            @RequestBody @Valid ApiRequest<CategoryAnalyticsRequestDto> request) {

        CategoryAnalyticsResponseDto result = categoryAnalyticsService.getAnalytics(
                request.getUser().getUserId(),
                request.getData()
        );
        return ResponseApi.success("Аналитика категории получена", result);
    }
}
