package pyc.lopatuxin.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pyc.lopatuxin.auth.AbstractIntegrationTest;
import pyc.lopatuxin.auth.dto.request.ApiRequest;
import pyc.lopatuxin.auth.dto.request.RegisterRequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest extends AbstractIntegrationTest {

    @Test
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

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Пользователь успешно зарегистрирован"))
                .andExpect(jsonPath("$.body").exists());
    }
}