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
import pyc.lopatuxin.budget.dto.request.CreateCategoryDto;
import pyc.lopatuxin.budget.dto.request.DeleteCategoryRequestDto;
import pyc.lopatuxin.budget.dto.request.UpdateCategoryRequestDto;
import pyc.lopatuxin.budget.dto.response.CategoryResponseDto;
import pyc.lopatuxin.budget.dto.response.ResponseApi;
import pyc.lopatuxin.budget.service.CategoryService;

/**
 * Контроллер для управления категориями расходов.
 */
@Slf4j
@RestController
@RequestMapping("/api/budget/categories")
@RequiredArgsConstructor
@Tag(name = "Категории", description = "API для управления категориями расходов")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Создаёт новую категорию для пользователя.
     *
     * @param request запрос с контекстом пользователя и данными категории
     * @return стандартный ответ с данными созданной категории
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Создать категорию",
            description = "Создаёт новую категорию расходов для пользователя"
    )
    @ApiResponse(
            responseCode = "201",
            description = "Категория успешно создана",
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
            responseCode = "409",
            description = "Категория с таким именем уже существует",
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
    public ResponseApi<CategoryResponseDto> createCategory(
            @RequestBody @Valid ApiRequest<CreateCategoryDto> request) {

        CategoryResponseDto result = categoryService.createCategory(
                request.getUser().getUserId(),
                request.getData()
        );
        return ResponseApi.created("Категория успешно создана", result);
    }

    /**
     * Обновляет категорию пользователя.
     *
     * @param request запрос с контекстом пользователя и данными для обновления
     * @return стандартный ответ с обновлёнными данными категории
     */
    @PostMapping("/update")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Обновить категорию",
            description = "Обновляет название и лимит бюджета категории расходов"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Категория успешно обновлена",
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
            responseCode = "409",
            description = "Категория с таким именем уже существует",
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
    public ResponseApi<CategoryResponseDto> updateCategory(
            @RequestBody @Valid ApiRequest<UpdateCategoryRequestDto> request) {

        CategoryResponseDto result = categoryService.updateCategory(
                request.getUser().getUserId(),
                request.getData()
        );
        return ResponseApi.success("Категория успешно обновлена", result);
    }

    /**
     * Удаляет категорию пользователя.
     *
     * @param request запрос с контекстом пользователя и идентификатором категории
     * @return стандартный ответ без тела
     */
    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Удалить категорию",
            description = "Удаляет категорию пользователя. Если есть связанные расходы и force=false/null, возвращает 409. При force=true удаляет категорию вместе со всеми её расходами."
    )
    @ApiResponse(responseCode = "200", description = "Категория успешно удалена",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseApi.class)))
    @ApiResponse(responseCode = "400", description = "Некорректные параметры запроса",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseApi.class)))
    @ApiResponse(responseCode = "401", description = "Не авторизован",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseApi.class)))
    @ApiResponse(responseCode = "404", description = "Категория не найдена",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseApi.class)))
    @ApiResponse(responseCode = "409", description = "Категория содержит расходы (требуется force=true для каскадного удаления)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseApi.class)))
    @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseApi.class)))
    public ResponseApi<Void> deleteCategory(
            @RequestBody @Valid ApiRequest<DeleteCategoryRequestDto> request) {

        categoryService.deleteCategory(
                request.getUser().getUserId(),
                request.getData()
        );
        return ResponseApi.success("Категория успешно удалена", null);
    }
}
