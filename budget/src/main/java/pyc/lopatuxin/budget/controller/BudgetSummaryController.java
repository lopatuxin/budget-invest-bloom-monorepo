package pyc.lopatuxin.budget.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pyc.lopatuxin.budget.dto.response.BudgetSummaryResponseDto;
import pyc.lopatuxin.budget.dto.response.ResponseApi;
import pyc.lopatuxin.budget.service.BudgetSummaryService;

import java.util.UUID;

/**
 * Контроллер для получения агрегированной сводки бюджета пользователя.
 *
 * <p>Примечание: параметр userId передаётся как query-параметр на время отсутствия API Gateway.
 * После внедрения Gateway userId будет извлекаться из заголовка X-User-Id или из JWT-токена.</p>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/summary")
@RequiredArgsConstructor
@Tag(name = "Бюджет", description = "API для управления бюджетом пользователя")
public class BudgetSummaryController {

    private final BudgetSummaryService budgetSummaryService;

    /**
     * Возвращает агрегированную сводку бюджета пользователя за указанный месяц и год.
     *
     * @param userId идентификатор пользователя (временно передаётся как query-параметр до появления API Gateway)
     * @param month  номер месяца (1-12)
     * @param year   год (2020-2100)
     * @return стандартный ответ со сводкой бюджета
     */
    @GetMapping
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
            description = "Некорректные параметры запроса (отсутствует userId, month вне диапазона 1-12, year вне диапазона 2020-2100)",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    @ApiResponse(
            responseCode = "401",
            description = "Не авторизован (после внедрения API Gateway)",
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
            @Parameter(description = "Идентификатор пользователя (UUID)", required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000")
            @NotNull(message = "Параметр userId обязателен")
            @RequestParam UUID userId,

            @Parameter(description = "Номер месяца (1-12)", required = true, example = "3")
            @NotNull(message = "Параметр month обязателен")
            @Min(value = 1, message = "Значение параметра month должно быть от 1 до 12")
            @Max(value = 12, message = "Значение параметра month должно быть от 1 до 12")
            @RequestParam Integer month,

            @Parameter(description = "Год (2020-2100)", required = true, example = "2024")
            @NotNull(message = "Параметр year обязателен")
            @Min(value = 2020, message = "Значение параметра year должно быть от 2020 до 2100")
            @Max(value = 2100, message = "Значение параметра year должно быть от 2020 до 2100")
            @RequestParam Integer year) {

        BudgetSummaryResponseDto summary = budgetSummaryService.getSummary(userId, month, year);
        return ResponseApi.success("Сводка бюджета успешно получена", summary);
    }
}