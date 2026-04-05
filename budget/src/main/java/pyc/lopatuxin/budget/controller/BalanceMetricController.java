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
import pyc.lopatuxin.budget.dto.response.MetricResponseDto;
import pyc.lopatuxin.budget.dto.response.ResponseApi;
import pyc.lopatuxin.budget.service.BalanceMetricService;

/**
 * Контроллер для получения детальной метрики баланса пользователя за год.
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/metric/balance")
@RequiredArgsConstructor
@Tag(name = "Метрики", description = "API для получения детальных метрик бюджета")
public class BalanceMetricController {

    private final BalanceMetricService balanceMetricService;

    /**
     * Возвращает детальную метрику баланса пользователя за указанный год.
     *
     * @param request запрос с контекстом пользователя и годом
     * @return стандартный ответ с метрикой баланса
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Получить метрику баланса",
            description = "Возвращает помесячную разбивку баланса (доходы минус расходы) за указанный год, " +
                    "а также агрегированные показатели: текущее значение, среднее, максимум и процент изменения."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Метрика баланса успешно получена",
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
    public ResponseApi<MetricResponseDto> getBalanceMetric(
            @RequestBody @Valid ApiRequest<YearDto> request) {

        MetricResponseDto metric = balanceMetricService.getBalanceMetric(
                request.getUser().getUserId(),
                request.getData().getYear()
        );
        return ResponseApi.success("Метрика баланса успешно получена", metric);
    }
}
