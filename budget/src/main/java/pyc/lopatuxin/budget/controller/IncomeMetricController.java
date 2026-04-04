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
import pyc.lopatuxin.budget.dto.common.YearDto;
import pyc.lopatuxin.budget.dto.response.IncomeMetricResponseDto;
import pyc.lopatuxin.budget.dto.response.ResponseApi;
import pyc.lopatuxin.budget.service.IncomeMetricService;

/**
 * Контроллер для получения детальной метрики доходов пользователя за год.
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/metric/income")
@RequiredArgsConstructor
@Tag(name = "Метрики", description = "API для получения детальных метрик бюджета")
public class IncomeMetricController {

    private final IncomeMetricService incomeMetricService;

    /**
     * Возвращает детальную метрику доходов пользователя за указанный год.
     *
     * @param request запрос с контекстом пользователя и годом
     * @return стандартный ответ с метрикой доходов
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Получить метрику доходов",
            description = "Возвращает помесячную разбивку доходов за указанный год, " +
                    "а также агрегированные показатели: текущее значение, среднее, максимум и процент изменения."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Метрика доходов успешно получена",
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
    public ResponseApi<IncomeMetricResponseDto> getIncomeMetric(
            @RequestBody @Valid ApiRequest<YearDto> request) {

        IncomeMetricResponseDto metric = incomeMetricService.getIncomeMetric(
                request.getUser().getUserId(),
                request.getData().getYear()
        );
        return ResponseApi.success("Метрика доходов успешно получена", metric);
    }
}
