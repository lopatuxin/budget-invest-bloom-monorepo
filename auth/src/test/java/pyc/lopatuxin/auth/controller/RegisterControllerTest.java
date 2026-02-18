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
import pyc.lopatuxin.auth.dto.request.RegisterRequest;
import pyc.lopatuxin.auth.entity.UserRole;
import pyc.lopatuxin.auth.enums.RoleName;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegisterControllerTest extends AbstractIntegrationTest {

    @Test
    @Transactional
    @DisplayName("Должен успешно зарегистрировать нового пользователя")
    void shouldRegisterNewUser() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("test@example.com")
                .password("password123")
                .firstName("Тест")
                .lastName("Пользователь")
                .build();

        ApiRequest<RegisterRequest> apiRequest = ApiRequest.<RegisterRequest>builder()
                .data(registerRequest)
                .build();

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Пользователь успешно зарегистрирован"))
        ;
    }

    @Test
    @Transactional
    @DisplayName("Должен возвращать конфликт при дублировании email")
    void shouldReturnConflictForDuplicateEmail() throws Exception {
        RegisterRequest firstRequest = RegisterRequest.builder()
                .email("duplicate@example.com")
                .password("password123")
                .firstName("Первый")
                .lastName("Пользователь")
                .build();

        ApiRequest<RegisterRequest> firstApiRequest = ApiRequest.<RegisterRequest>builder()
                .data(firstRequest)
                .build();

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstApiRequest)))
                .andExpect(status().isCreated());

        RegisterRequest duplicateRequest = RegisterRequest.builder()
                .email("duplicate@example.com")
                .password("password456")
                .firstName("Второй")
                .lastName("Пользователь")
                .build();

        ApiRequest<RegisterRequest> duplicateApiRequest = ApiRequest.<RegisterRequest>builder()
                .data(duplicateRequest)
                .build();

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateApiRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Пользователь с такой почтой уже существует"));
    }

    @Test
    @Transactional
    @DisplayName("Должен создавать роль пользователя при регистрации")
    void shouldCreateUserRoleWhenRegisteringNewUser() throws Exception {
        String testEmail = "role-test@example.com";
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(testEmail)
                .password("password123")
                .firstName("Тест")
                .lastName("Роли")
                .build();

        ApiRequest<RegisterRequest> apiRequest = ApiRequest.<RegisterRequest>builder()
                .data(registerRequest)
                .build();

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isCreated());

        List<UserRole> userRoles = userRoleRepository.findAll();
        assertEquals(1, userRoles.size(), "У пользователя должна быть ровно одна роль");
        assertEquals(RoleName.USER, userRoles.getFirst().getRoleName(), "Роль пользователя должна быть USER");
    }

    @ParameterizedTest
    @MethodSource("invalidRegisterRequestData")
    @DisplayName("Должен возвращать ошибку валидации для некорректных данных")
    void shouldReturnValidationErrorForInvalidData(String email, String password, String firstName, String lastName, String expectedMessage) throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password(password)
                .firstName(firstName)
                .lastName(lastName)
                .build();

        ApiRequest<RegisterRequest> apiRequest = ApiRequest.<RegisterRequest>builder()
                .data(registerRequest)
                .build();

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    private static Stream<Arguments> invalidRegisterRequestData() {
        return Stream.of(
                Arguments.of(null, "password123", "Имя", "Фамилия", "Email обязателен"),
                Arguments.of("", "password123", "Имя", "Фамилия", "Email обязателен"),
                Arguments.of("invalid-email", "password123", "Имя", "Фамилия", "Неверный формат email"),
                Arguments.of("test@example.com", null, "Имя", "Фамилия", "Пароль обязателен"),
                Arguments.of("test@example.com", "", "Имя", "Фамилия", "Пароль обязателен"),
                Arguments.of("test@example.com", "123", "Имя", "Фамилия", "Пароль должен содержать минимум 6 символов"),
                Arguments.of("test@example.com", "password123", null, "Фамилия", "Имя обязательно"),
                Arguments.of("test@example.com", "password123", "", "Фамилия", "Имя обязательно"),
                Arguments.of("test@example.com", "password123", "Имя", null, "Фамилия обязательна"),
                Arguments.of("test@example.com", "password123", "Имя", "", "Фамилия обязательна")
        );
    }
}