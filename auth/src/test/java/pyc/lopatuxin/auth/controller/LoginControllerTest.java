package pyc.lopatuxin.auth.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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

    private static final String WRONG_PASSWORD_TEST_EMAIL = "wrong-password-test@example.com";
    private static final String LOCKED_ACCOUNT_TEST_EMAIL = "locked-response-test@example.com";

    @Autowired
    @Qualifier("authTransactionManager")
    private PlatformTransactionManager transactionManager;

    // Not @Transactional: handleFailedLogin() now runs in its own REQUIRES_NEW transaction, which
    // cannot see a user row inserted by an uncommitted outer test transaction. This test performs
    // a real HTTP login against a committed user and cleans up manually afterwards, exactly like
    // LoginLockoutIT.
    @AfterEach
    void cleanUpWrongPasswordTestUser() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                userRepository.findUserByEmail(WRONG_PASSWORD_TEST_EMAIL).ifPresent(user -> {
                    refreshTokenRepository.deleteAllByUser(user);
                    userRepository.delete(user);
                }));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                userRepository.findUserByEmail(LOCKED_ACCOUNT_TEST_EMAIL).ifPresent(user -> {
                    refreshTokenRepository.deleteAllByUser(user);
                    userRepository.delete(user);
                }));
    }

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

        mockMvc.perform(post("/auth/api/login")
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

        mockMvc.perform(post("/auth/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Неверный email или пароль"))
                .andExpect(jsonPath("$.body.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Должен возвращать ошибку 401 при неверном пароле")
    void shouldReturnUnauthorizedWhenPasswordIsIncorrect() throws Exception {
        User user = createUser();
        user.setEmail(WRONG_PASSWORD_TEST_EMAIL);
        userRepository.save(user);

        LoginRequest loginRequest = LoginRequest.builder()
                .email(WRONG_PASSWORD_TEST_EMAIL)
                .password("wrongPassword")
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        mockMvc.perform(post("/auth/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Неверный email или пароль"))
                .andExpect(jsonPath("$.body.code").value("INVALID_CREDENTIALS"));
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

        mockMvc.perform(post("/auth/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Пользователь не активирован"))
                .andExpect(jsonPath("$.body.code").value("ACCOUNT_INACTIVE"));
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

        mockMvc.perform(post("/auth/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.body.code").value("MISSING_REQUIRED_FIELDS"));
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

    // Not @Transactional, same reasoning as shouldReturnUnauthorizedWhenPasswordIsIncorrect above:
    // this checks the full response contract (status, message, code) for an already-locked
    // account, which LoginLockoutIT's loginToLockedAccountIsRejected does not assert (it only
    // checks status and code). Kept here against a real committed user with manual cleanup so the
    // response assertions are not shielded by a shared test/service transaction.
    @Test
    @DisplayName("Должен возвращать ошибку 403 при попытке входа в заблокированный аккаунт")
    void shouldReturnForbiddenWhenAccountIsLocked() throws Exception {
        User lockedUser = createUser();
        lockedUser.setEmail(LOCKED_ACCOUNT_TEST_EMAIL);
        lockedUser.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        lockedUser.setFailedLoginAttempts(5);
        userRepository.save(lockedUser);

        LoginRequest loginRequest = LoginRequest.builder()
                .email(LOCKED_ACCOUNT_TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder()
                .data(loginRequest)
                .build();

        mockMvc.perform(post("/auth/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Аккаунт заблокирован"))
                .andExpect(jsonPath("$.body.code").value("ACCOUNT_LOCKED"));
    }
}