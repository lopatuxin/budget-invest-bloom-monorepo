package pyc.lopatuxin.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.auth.AbstractIntegrationTest;
import pyc.lopatuxin.auth.dto.request.ApiRequest;
import pyc.lopatuxin.auth.dto.request.LoginRequest;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginControllerTest extends AbstractIntegrationTest {

    @Test
    @Transactional
    @DisplayName("Должен успешно обработать запрос на аутентификацию")
    void shouldHandleLoginRequest() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Аутентификация прошла успешно"))
                .andExpect(jsonPath("$.body.accessToken").value("sample.access.token"))
                .andExpect(jsonPath("$.body.refreshToken").value("sample.refresh.token"))
                .andExpect(jsonPath("$.body.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.body.expiresIn").value(900))
                .andExpect(jsonPath("$.body.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.body.user.isActive").value(true))
                .andExpect(jsonPath("$.body.user.isVerified").value(true))
                .andExpect(jsonPath("$.body.user.roles").isArray())
                .andExpect(jsonPath("$.body.user.roles[0]").value("USER"));
    }

    @ParameterizedTest
    @MethodSource("invalidLoginRequestData")
    @DisplayName("Должен возвращать ошибку валидации для некорректных данных аутентификации")
    void shouldReturnValidationErrorForInvalidLoginData(String email, String password, String expectedMessage) throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    private static Stream<Arguments> invalidLoginRequestData() {
        return Stream.of(
                Arguments.of(null, "password123", "Email адрес обязателен"),
                Arguments.of("", "password123", "Email адрес обязателен"),
                Arguments.of("   ", "password123", "Email адрес обязателен"),
                Arguments.of("invalid-email", "password123", "Некорректный формат email адреса"),
                Arguments.of("user@", "password123", "Некорректный формат email адреса"),
                Arguments.of("@example.com", "password123", "Некорректный формат email адреса"),
                Arguments.of("test@example.com", null, "Пароль обязателен"),
                Arguments.of("test@example.com", "", "Пароль обязателен"),
                Arguments.of("test@example.com", "   ", "Пароль обязателен")
        );
    }
}