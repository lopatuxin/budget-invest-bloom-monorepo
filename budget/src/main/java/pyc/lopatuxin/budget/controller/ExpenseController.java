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
import pyc.lopatuxin.budget.dto.request.CreateExpenseDto;
import pyc.lopatuxin.budget.dto.request.DeleteExpenseRequestDto;
import pyc.lopatuxin.budget.dto.response.ExpenseResponseDto;
import pyc.lopatuxin.budget.dto.response.ResponseApi;
import pyc.lopatuxin.budget.service.ExpenseService;

/**
 * Контроллер для управления расходами пользователя.
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/expenses")
@RequiredArgsConstructor
@Tag(name = "Расходы", description = "API для управления расходами")
public class ExpenseController {

    private final ExpenseService expenseService;

    /**
     * Создаёт новый расход для пользователя.
     *
     * @param request запрос с контекстом пользователя и данными расхода
     * @return стандартный ответ с данными созданного расхода
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Добавить расход",
            description = "Создаёт новый расход для пользователя в указанной категории. " +
                    "Если дата не указана — используется текущая дата."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Расход успешно создан",
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
    public ResponseApi<ExpenseResponseDto> createExpense(
            @RequestBody @Valid ApiRequest<CreateExpenseDto> request) {

        ExpenseResponseDto result = expenseService.createExpense(
                request.getUser().getUserId(),
                request.getData()
        );
        return ResponseApi.created("Расход успешно добавлен", result);
    }

    /**
     * Удаляет расход пользователя.
     *
     * @param request запрос с контекстом пользователя и идентификатором расхода
     * @return стандартный ответ без тела
     */
    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Удалить расход",
            description = "Удаляет расход пользователя по идентификатору"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Расход успешно удалён",
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
            description = "Расход не найден",
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
    public ResponseApi<Void> deleteExpense(
            @RequestBody @Valid ApiRequest<DeleteExpenseRequestDto> request) {

        expenseService.deleteExpense(
                request.getUser().getUserId(),
                request.getData()
        );
        return ResponseApi.success("Расход успешно удалён", null);
    }
}
