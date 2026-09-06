package pyc.lopatuxin.budget.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pyc.lopatuxin.shared.dto.ResponseApi;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleBudgetConflict: доменный конфликт отдаётся как 409 с исходным сообщением")
    void handleBudgetConflict_shouldReturn409WithOriginalMessage() {
        BudgetConflictException ex = new BudgetConflictException("Системную категорию «Инвестиции» нельзя удалить");

        ResponseEntity<ResponseApi<Object>> response = handler.handleBudgetConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("Системную категорию «Инвестиции» нельзя удалить");
    }

    @Test
    @DisplayName("handleGenericException: техническое исключение (в т.ч. IllegalStateException) отдаётся как 500 без утечки текста")
    void handleGenericException_shouldReturn500WithoutLeakingExceptionText() {
        IllegalStateException ex = new IllegalStateException("some internal detail that must not leak to the client");

        ResponseEntity<ResponseApi<Object>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("Внутренняя ошибка сервера");
        assertThat(response.getBody().getMessage()).doesNotContain("internal detail");
    }
}
