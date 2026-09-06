package pyc.lopatuxin.budget.controller;

import io.swagger.v3.oas.annotations.Operation;
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
import pyc.lopatuxin.budget.dto.request.CreateIncomeDto;
import pyc.lopatuxin.budget.dto.request.DeleteIncomeRequestDto;
import pyc.lopatuxin.budget.dto.response.IncomeResponseDto;
import pyc.lopatuxin.budget.service.IncomeService;
import pyc.lopatuxin.shared.dto.ApiRequest;
import pyc.lopatuxin.shared.dto.ResponseApi;

/** REST-контроллер для управления доходами пользователя. */
@Slf4j
@RestController
@RequestMapping("/api/budget/incomes")
@RequiredArgsConstructor
@Tag(name = "Доходы", description = "API для управления доходами")
public class IncomeController {

    private final IncomeService incomeService;

    /**
     * Добавляет новый доход для аутентифицированного пользователя.
     *
     * @param request обёртка с контекстом пользователя и данными дохода
     * @return DTO созданного дохода с кодом 201
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Добавить доход")
    @ApiResponse(responseCode = "201", description = "Доход успешно добавлен")
    @ApiResponse(responseCode = "400", description = "Некорректные данные")
    @CommonApiResponses
    public ResponseApi<IncomeResponseDto> createIncome(
            @RequestBody @Valid ApiRequest<CreateIncomeDto> request) {
        IncomeResponseDto result = incomeService.createIncome(
                request.getUser().getUserId(),
                request.getData()
        );
        return ResponseApi.created("Доход успешно добавлен", result);
    }

    /**
     * Удаляет доход пользователя.
     *
     * @param request запрос с контекстом пользователя и идентификатором дохода
     * @return стандартный ответ без тела
     */
    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Удалить доход", description = "Удаляет доход пользователя по идентификатору")
    @ApiResponse(responseCode = "200", description = "Доход успешно удалён")
    @ApiResponse(responseCode = "400", description = "Некорректные параметры запроса")
    @ApiResponse(responseCode = "404", description = "Доход не найден")
    @CommonApiResponses
    public ResponseApi<Void> deleteIncome(
            @RequestBody @Valid ApiRequest<DeleteIncomeRequestDto> request) {
        incomeService.deleteIncome(
                request.getUser().getUserId(),
                request.getData()
        );
        return ResponseApi.success("Доход успешно удалён", null);
    }
}
