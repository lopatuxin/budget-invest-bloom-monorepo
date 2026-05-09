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
import pyc.lopatuxin.budget.dto.common.InternalApiRequest;
import pyc.lopatuxin.budget.dto.request.DeleteInvestmentEntryRequestDto;
import pyc.lopatuxin.budget.dto.request.InvestmentEntryRequestDto;
import pyc.lopatuxin.budget.dto.response.InvestmentEntryResponseDto;
import pyc.lopatuxin.budget.dto.response.ResponseApi;
import pyc.lopatuxin.budget.service.InvestmentEntryService;

/**
 * Internal controller for recording investment operations as budget entries.
 *
 * <p>This endpoint is intended for service-to-service communication within the docker-compose network.
 * No JWT authentication is performed — the caller (investment service) is trusted by network policy.
 * The userId is still extracted from the user block populated upstream by the API Gateway.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/internal/investment-entry")
@RequiredArgsConstructor
@Tag(name = "Internal: инвестиционные операции",
        description = "Внутренний API для регистрации инвестиционных операций в бюджете (только для investment-сервиса)")
public class InternalInvestmentEntryController {

    private final InvestmentEntryService investmentEntryService;

    /**
     * Records an investment operation (BUY or SELL) as a budget entry.
     *
     * @param request request with user context and investment entry data
     * @return standard response with the UUID of the created entry
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Создать инвестиционную запись",
            description = "BUY → создаёт Expense в системной категории «Инвестиции». " +
                    "SELL → создаёт Income с источником INVESTMENTS."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Запись успешно создана",
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
            responseCode = "409",
            description = "Конфликт имени системной категории с пользовательской категорией",
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
    public ResponseApi<InvestmentEntryResponseDto> createEntry(
            @RequestBody @Valid InternalApiRequest<InvestmentEntryRequestDto> request) {

        InvestmentEntryResponseDto result = investmentEntryService.create(
                request.getUser().getUserId(),
                request.getData()
        );
        return ResponseApi.created("Инвестиционная запись успешно создана", result);
    }

    /**
     * Deletes the budget entry corresponding to an investment operation.
     * Idempotent: if the record is not found, returns 200 without error.
     *
     * @param request request with user context and entry identification data
     * @return standard response without body
     */
    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Удалить инвестиционную запись",
            description = "Удаляет Expense (BUY) или Income (SELL) по entryId. Идемпотентно: если запись не найдена — возвращает 200."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Запись успешно удалена (или не найдена — идемпотентно)",
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
            responseCode = "500",
            description = "Внутренняя ошибка сервера",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseApi.class)
            )
    )
    public ResponseApi<Void> deleteEntry(
            @RequestBody @Valid InternalApiRequest<DeleteInvestmentEntryRequestDto> request) {

        investmentEntryService.delete(
                request.getUser().getUserId(),
                request.getData().getEntryId(),
                request.getData().getType()
        );
        return ResponseApi.success("Инвестиционная запись успешно удалена", null);
    }
}
