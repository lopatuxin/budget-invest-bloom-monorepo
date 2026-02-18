package pyc.lopatuxin.auth.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.auth.AbstractIntegrationTest;
import pyc.lopatuxin.auth.dto.request.ApiRequest;
import pyc.lopatuxin.auth.dto.request.LogoutRequest;
import pyc.lopatuxin.auth.entity.RefreshToken;
import pyc.lopatuxin.auth.entity.User;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogoutControllerTest extends AbstractIntegrationTest {

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @Transactional
    @DisplayName("Должен успешно обработать запрос на выход из текущей сессии")
    void shouldHandleLogoutRequest() throws Exception {
        User user = createUser();
        userRepository.save(user);

        String jwtToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, "Test-Agent", "127.0.0.1");

        LogoutRequest logoutRequest = LogoutRequest.builder()
                .logoutFromAll(false)
                .build();

        ApiRequest<LogoutRequest> apiRequest = ApiRequest.<LogoutRequest>builder()
                .data(logoutRequest)
                .build();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + jwtToken)
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("User-Agent", "Test-Agent")
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Вы успешно вышли из системы"))
                .andExpect(jsonPath("$.body.message").value("Вы успешно вышли из системы"))
                .andExpect(jsonPath("$.body.loggedOut").value(1))
                .andExpect(jsonPath("$.body.timestamp").exists());

        List<RefreshToken> tokens = refreshTokenRepository.findActiveTokensByUser(user, LocalDateTime.now());
        assertThat(tokens).isEmpty();

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getLastLogoutAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("Должен вернуть ошибку 400 при попытке выхода без refresh token")
    void shouldReturn400WhenLogoutWithoutRefreshToken() throws Exception {
        User user = createUser();
        userRepository.save(user);

        String jwtToken = jwtService.generateAccessToken(user);

        LogoutRequest logoutRequest = LogoutRequest.builder()
                .logoutFromAll(false)
                .build();

        ApiRequest<LogoutRequest> apiRequest = ApiRequest.<LogoutRequest>builder()
                .data(logoutRequest)
                .build();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("User-Agent", "Test-Agent")
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Для выхода из текущей сессии необходимо передать refresh token в cookie refreshToken. Используйте logoutFromAll: true для выхода со всех устройств без токена."));
    }

    @Test
    @Transactional
    @DisplayName("Должен успешно обработать выход со всех устройств (logoutFromAll = true)")
    void shouldHandleLogoutFromAllDevices() throws Exception {
        User user = createUser();
        userRepository.save(user);

        String jwtToken = jwtService.generateAccessToken(user);
        String refreshToken1 = jwtService.generateRefreshToken(user);
        String refreshToken2 = jwtService.generateRefreshToken(user);
        String refreshToken3 = jwtService.generateRefreshToken(user);

        refreshTokenService.createRefreshToken(user, refreshToken1, "Device-1", "192.168.1.1");
        refreshTokenService.createRefreshToken(user, refreshToken2, "Device-2", "192.168.1.2");
        refreshTokenService.createRefreshToken(user, refreshToken3, "Device-3", "192.168.1.3");

        int initialSecurityVersion = user.getSecurityVersion();

        LogoutRequest logoutRequest = LogoutRequest.builder()
                .logoutFromAll(true)
                .build();

        ApiRequest<LogoutRequest> apiRequest = ApiRequest.<LogoutRequest>builder()
                .data(logoutRequest)
                .build();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("User-Agent", "Test-Agent")
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.body.message").value("Вы успешно вышли из системы на всех устройствах"))
                .andExpect(jsonPath("$.body.loggedOut").value(3))
                .andExpect(jsonPath("$.body.timestamp").exists());

        List<RefreshToken> tokens = refreshTokenRepository.findActiveTokensByUser(user, LocalDateTime.now());
        assertThat(tokens).isEmpty();

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getSecurityVersion()).isEqualTo(initialSecurityVersion + 1);
        assertThat(updatedUser.getLastLogoutAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("Должен успешно обработать повторный вызов logout (idempotency)")
    void shouldHandleRepeatedLogoutIdempotently() throws Exception {
        User user = createUser();
        userRepository.save(user);

        String jwtToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, "Test-Agent", "127.0.0.1");

        LogoutRequest logoutRequest = LogoutRequest.builder()
                .logoutFromAll(false)
                .build();

        ApiRequest<LogoutRequest> apiRequest = ApiRequest.<LogoutRequest>builder()
                .data(logoutRequest)
                .build();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + jwtToken)
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("User-Agent", "Test-Agent")
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + jwtToken)
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("User-Agent", "Test-Agent")
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.loggedOut").value(0));
    }

    @Test
    @Transactional
    @DisplayName("Должен вернуть 401 при отсутствии access token")
    void shouldReturn401WhenMissingAccessToken() throws Exception {
        LogoutRequest logoutRequest = LogoutRequest.builder()
                .logoutFromAll(false)
                .build();

        ApiRequest<LogoutRequest> apiRequest = ApiRequest.<LogoutRequest>builder()
                .data(logoutRequest)
                .build();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    @DisplayName("Должен вернуть 401 при недействительном access token")
    void shouldReturn401WhenInvalidAccessToken() throws Exception {
        LogoutRequest logoutRequest = LogoutRequest.builder()
                .logoutFromAll(false)
                .build();

        ApiRequest<LogoutRequest> apiRequest = ApiRequest.<LogoutRequest>builder()
                .data(logoutRequest)
                .build();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer invalid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    @DisplayName("Должен успешно обработать logout с несуществующим refresh token (idempotency)")
    void shouldHandleLogoutWithNonExistentRefreshToken() throws Exception {
        User user = createUser();
        userRepository.save(user);

        String jwtToken = jwtService.generateAccessToken(user);
        String nonExistentRefreshToken = jwtService.generateRefreshToken(user);

        LogoutRequest logoutRequest = LogoutRequest.builder()
                .logoutFromAll(false)
                .build();

        ApiRequest<LogoutRequest> apiRequest = ApiRequest.<LogoutRequest>builder()
                .data(logoutRequest)
                .build();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + jwtToken)
                        .cookie(new Cookie("refreshToken", nonExistentRefreshToken))
                        .header("User-Agent", "Test-Agent")
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.loggedOut").value(0));
    }

    @Test
    @Transactional
    @DisplayName("Должен корректно обработать logoutFromAll когда нет активных сессий")
    void shouldHandleLogoutFromAllWithNoActiveSessions() throws Exception {
        User user = createUser();
        userRepository.save(user);

        String jwtToken = jwtService.generateAccessToken(user);
        int initialSecurityVersion = user.getSecurityVersion();

        LogoutRequest logoutRequest = LogoutRequest.builder()
                .logoutFromAll(true)
                .build();

        ApiRequest<LogoutRequest> apiRequest = ApiRequest.<LogoutRequest>builder()
                .data(logoutRequest)
                .build();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("User-Agent", "Test-Agent")
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.message").value("Вы успешно вышли из системы на всех устройствах"))
                .andExpect(jsonPath("$.body.loggedOut").value(1));

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getSecurityVersion()).isEqualTo(initialSecurityVersion + 1);
    }
}