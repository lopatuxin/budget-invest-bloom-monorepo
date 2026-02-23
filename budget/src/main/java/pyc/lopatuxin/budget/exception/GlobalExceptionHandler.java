package pyc.lopatuxin.budget.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pyc.lopatuxin.budget.dto.response.ResponseApi;

import java.util.HashMap;
import java.util.Map;

/**
 * Глобальный обработчик исключений для budget-сервиса.
 * Перехватывает исключения и преобразует их в стандартный ResponseApi-ответ.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Обрабатывает ошибки валидации тела запроса (@Valid на @RequestBody).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseApi<Map<String, String>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        log.warn("Ошибка валидации запроса: {}", fieldErrors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ResponseApi.error(HttpStatus.BAD_REQUEST.value(), "Ошибка валидации запроса", fieldErrors));
    }

    /**
     * Обрабатывает ошибки валидации параметров метода (@NotNull, @Min, @Max на @RequestParam).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseApi<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Некорректные параметры запроса: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ResponseApi.error(HttpStatus.BAD_REQUEST.value(), "Некорректные параметры запроса"));
    }

    /**
     * Обрабатывает некорректные бизнес-аргументы.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseApi<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Некорректный аргумент: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ResponseApi.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /**
     * Обрабатывает случай, когда запрошенная сущность не найдена в БД.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ResponseApi<Object>> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Сущность не найдена: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ResponseApi.error(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    /**
     * Обрабатывает отсутствие обязательного параметра запроса.
     * Возникает, когда @RequestParam-параметр помечен как required=true,
     * но не передан в запросе.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseApi<Object>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex) {
        log.warn("Отсутствует обязательный параметр запроса: {}", ex.getParameterName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ResponseApi.error(HttpStatus.BAD_REQUEST.value(), "Отсутствует обязательный параметр: " + ex.getParameterName()));
    }

    /**
     * Обрабатывает все непредвиденные исключения.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseApi<Object>> handleGenericException(Exception ex) {
        log.error("Внутренняя ошибка сервера: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseApi.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Внутренняя ошибка сервера"));
    }
}