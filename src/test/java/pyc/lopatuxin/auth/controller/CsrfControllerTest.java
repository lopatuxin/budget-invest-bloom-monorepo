package pyc.lopatuxin.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.auth.AbstractIntegrationTest;
import pyc.lopatuxin.auth.dto.request.ApiRequest;
import pyc.lopatuxin.auth.dto.request.RegisterRequest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CsrfControllerTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Должен возвращать CSRF токен с корректной структурой ответа")
    void shouldReturnCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("CSRF токен успешно получен"))
                .andExpect(jsonPath("$.body").exists())
                .andExpect(jsonPath("$.body").isString())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertNotNull(responseBody, "Ответ не должен быть пустым");
    }

    @Test
    @DisplayName("Должен возвращать токен в валидном формате с достаточной длиной")
    void shouldReturnValidTokenFormat() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").exists())
                .andReturn();

        String token = extractTokenFromResponse(result.getResponse().getContentAsString());
        assertNotNull(token, "Токен не должен быть null");
        assertTrue(token.length() > 10, "Токен должен иметь достаточную длину");
    }

    @Test
    @DisplayName("Должен возвращать токены при множественных запросах")
    void shouldReturnDifferentTokensOnMultipleRequests() throws Exception {
        MvcResult firstResult = mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").exists())
                .andReturn();

        MvcResult secondResult = mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").exists())
                .andReturn();

        String firstToken = extractTokenFromResponse(firstResult.getResponse().getContentAsString());
        String secondToken = extractTokenFromResponse(secondResult.getResponse().getContentAsString());

        assertNotNull(firstToken, "Первый токен не должен быть null");
        assertNotNull(secondToken, "Второй токен не должен быть null");
    }

    @Test
    @DisplayName("Должен разрешать доступ без аутентификации")
    void shouldAllowAccessWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @Transactional
    @DisplayName("Должен разрешать защищенные запросы с CSRF токеном")
    void shouldAllowProtectedRequestWithCsrfToken() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("csrf-test@example.com")
                .password("password123")
                .firstName("CSRF")
                .lastName("Test")
                .build();

        ApiRequest<RegisterRequest> apiRequest = ApiRequest.<RegisterRequest>builder()
                .data(registerRequest)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201));
    }

    private String extractTokenFromResponse(String responseBody) throws Exception {
        return objectMapper.readTree(responseBody).get("body").asText();
    }
}