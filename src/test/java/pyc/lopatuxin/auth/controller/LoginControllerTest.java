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
import pyc.lopatuxin.auth.entity.User;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginControllerTest extends AbstractIntegrationTest {

    @Test
    @Transactional
    @DisplayName("Должен успешно обработать запрос на аутентификацию")
    void shouldHandleLoginRequest() throws Exception {
        User user = createUser();
        userRepository.save(user);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password(TEST_PASSWORD)
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
                .andExpect(jsonPath("$.body.accessToken").exists())
                .andExpect(jsonPath("$.body.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.body.expiresIn").value(900))
                .andExpect(jsonPath("$.body.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.body.user.isActive").value(true))
                .andExpect(jsonPath("$.body.user.isVerified").value(true));
    }

    @Test
    @Transactional
    @DisplayName("Должен возвращать ошибку 401 когда пользователь с указанной почтой не найден")
    void shouldReturnUnauthorizedWhenUserDoesNotExist() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("password123")
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Неверный email или пароль"));
    }

    @Test
    @Transactional
    @DisplayName("Должен возвращать ошибку 401 при неверном пароле")
    void shouldReturnUnauthorizedWhenPasswordIsIncorrect() throws Exception {
        User user = createUser();
        userRepository.save(user);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("wrongPassword")
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Неверный email или пароль"));
    }

    @Test
    @Transactional
    @DisplayName("Должен возвращать ошибку 403 когда пользователь не активирован")
    void shouldReturnForbiddenWhenUserIsNotActive() throws Exception {
        User inactiveUser = createUser();
        inactiveUser.setIsActive(false);
        inactiveUser.setEmail("inactive@example.com");
        userRepository.save(inactiveUser);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("inactive@example.com")
                .password(TEST_PASSWORD)
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Пользователь не активирован"));
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

    @Test
    @Transactional
    @DisplayName("Должен увеличивать счетчик неудачных попыток при неверном пароле")
    void shouldIncrementFailedAttemptsOnWrongPassword() throws Exception {
        User user = createUser();
        userRepository.save(user);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("wrongPassword")
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isUnauthorized());

        User updatedUser = userRepository.findUserByEmail("test@example.com").orElseThrow();
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("Должен постепенно увеличивать счетчик при множественных неудачных попытках")
    void shouldIncrementFailedAttemptsProgressively() throws Exception {
        User user = createUser();
        userRepository.save(user);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("wrongPassword")
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(apiRequest)))
                    .andExpect(status().isUnauthorized());

            User updatedUser = userRepository.findUserByEmail("test@example.com").orElseThrow();
            assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(i);
            assertThat(updatedUser.getLockedUntil()).isNull();
        }
    }

    @Test
    @Transactional
    @DisplayName("Должен блокировать аккаунт после 5 неудачных попыток")
    void shouldLockAccountAfterFiveFailedAttempts() throws Exception {
        User user = createUser();
        userRepository.save(user);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("wrongPassword")
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(apiRequest)))
                    .andExpect(status().isUnauthorized());
        }

        User updatedUser = userRepository.findUserByEmail("test@example.com").orElseThrow();
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(updatedUser.getLockedUntil()).isNotNull();
        assertThat(updatedUser.getLockedUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    @Transactional
    @DisplayName("Должен возвращать ошибку 403 при попытке входа в заблокированный аккаунт")
    void shouldReturnForbiddenWhenAccountIsLocked() throws Exception {
        User lockedUser = createUser();
        lockedUser.setEmail("locked@example.com");
        lockedUser.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        lockedUser.setFailedLoginAttempts(5);
        userRepository.save(lockedUser);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("locked@example.com")
                .password(TEST_PASSWORD)
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Аккаунт заблокирован до " + lockedUser.getLockedUntil()));
    }

    @Test
    @Transactional
    @DisplayName("Должен разблокировать аккаунт и сбросить счетчик после истечения времени блокировки")
    void shouldUnlockAccountAfterLockPeriodExpires() throws Exception {
        User user = createUser();
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        userRepository.save(user);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password(TEST_PASSWORD)
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findUserByEmail("test@example.com").orElseThrow();
        assertThat(updatedUser.getFailedLoginAttempts()).isZero();
        assertThat(updatedUser.getLockedUntil()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("Должен сбрасывать счетчик неудачных попыток после успешного входа")
    void shouldResetFailedAttemptsAfterSuccessfulLogin() throws Exception {
        User user = createUser();
        user.setFailedLoginAttempts(3);
        userRepository.save(user);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password(TEST_PASSWORD)
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findUserByEmail("test@example.com").orElseThrow();
        assertThat(updatedUser.getFailedLoginAttempts()).isZero();
    }

    @Test
    @Transactional
    @DisplayName("Должен блокировать аккаунт ровно на 15 минут после 5 неудачных попыток")
    void shouldLockAccountForExactly15MinutesAfterFiveFailedAttempts() throws Exception {
        User user = createUser();
        user.setFailedLoginAttempts(4);
        userRepository.save(user);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("wrongPassword")
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        java.time.LocalDateTime beforeAttempt = java.time.LocalDateTime.now();

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isUnauthorized());

        java.time.LocalDateTime afterAttempt = java.time.LocalDateTime.now();

        User updatedUser = userRepository.findUserByEmail("test@example.com").orElseThrow();
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(updatedUser.getLockedUntil()).isNotNull();

        LocalDateTime expectedLockTime = beforeAttempt.plusMinutes(15);
        LocalDateTime actualLockTime = updatedUser.getLockedUntil();

        assertThat(actualLockTime)
                .isAfter(expectedLockTime.minusSeconds(5))
                .isBefore(afterAttempt.plusMinutes(15).plusSeconds(5));
    }
}