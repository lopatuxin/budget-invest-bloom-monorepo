package pyc.lopatuxin.investment.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pyc.lopatuxin.investment.dto.response.ResponseApi;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseApi<Map<String, String>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError fieldError) {
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
            } else {
                fieldErrors.put(error.getObjectName(), error.getDefaultMessage());
            }
        });

        log.warn("Ошибка валидации запроса: {}", fieldErrors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ResponseApi.error(HttpStatus.BAD_REQUEST.value(), "Ошибка валидации запроса", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseApi<Object>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {
        log.warn("Некорректный формат данных в запросе: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ResponseApi.error(HttpStatus.BAD_REQUEST.value(), "Некорректный формат данных"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseApi<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Некорректные параметры запроса: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ResponseApi.error(HttpStatus.BAD_REQUEST.value(), "Некорректные параметры запроса"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseApi<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Некорректный аргумент: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ResponseApi.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ResponseApi<Object>> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Сущность не найдена: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ResponseApi.error(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseApi<Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        String rootMessage = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();
        log.warn("Нарушение ограничения целостности данных: {}", rootMessage);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ResponseApi.error(HttpStatus.CONFLICT.value(), "Запись с такими данными уже существует"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseApi<Object>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex) {
        log.warn("Отсутствует обязательный параметр запроса: {}", ex.getParameterName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ResponseApi.error(HttpStatus.BAD_REQUEST.value(), "Отсутствует обязательный параметр: " + ex.getParameterName()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseApi<Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        log.warn("Неподдерживаемый HTTP-метод: {}", ex.getMethod());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ResponseApi.error(HttpStatus.METHOD_NOT_ALLOWED.value(),
                        "HTTP-метод " + ex.getMethod() + " не поддерживается для данного эндпоинта"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ResponseApi<Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Недопустимая операция: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ResponseApi.error(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseApi<Object>> handleGenericException(Exception ex) {
        log.error("Внутренняя ошибка сервера: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseApi.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Внутренняя ошибка сервера"));
    }
}
