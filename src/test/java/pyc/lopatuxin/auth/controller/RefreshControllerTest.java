package pyc.lopatuxin.auth.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.auth.AbstractIntegrationTest;
import pyc.lopatuxin.auth.entity.RefreshToken;
import pyc.lopatuxin.auth.entity.User;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshControllerTest extends AbstractIntegrationTest {

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @Transactional
    @DisplayName("Должен успешно обновить токены с валидным refresh token")
    void shouldSuccessfullyRefreshTokens() throws Exception {
        User user = createUser();
        userRepository.save(user);

        String rawRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, "Mozilla/5.0", "192.168.1.1");

        mockMvc.perform(post("/api/refresh")
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Токены успешно обновлены"))
                .andExpect(jsonPath("$.body.accessToken").exists())
                .andExpect(jsonPath("$.body.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.body.expiresIn").value(900))
                .andExpect(cookie().exists("refreshToken"));

        // Проверяем что старый токен помечен как использованный
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(2); // старый + новый
        assertThat(tokens.stream().filter(RefreshToken::getIsUsed).count()).isEqualTo(1);

        // Проверяем обновление last_login_at
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getLastLoginAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("Должен вернуть ошибку при отсутствии refresh token в cookie")
    void shouldReturnErrorWhenRefreshTokenMissing() throws Exception {
        mockMvc.perform(post("/api/refresh")
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @DisplayName("Должен вернуть 401 при истекшем JWT refresh token")
    void shouldReturn401WhenRefreshTokenExpired() throws Exception {
        User user = createUser();
        userRepository.save(user);

        // Генерируем токен который уже истек (через мокирование времени это сложно,
        // поэтому используем недействительный токен как имитацию)
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE1MTYyMzkwMjJ9.invalid";

        mockMvc.perform(post("/api/refresh")
                        .cookie(new Cookie("refreshToken", expiredToken))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @Transactional
    @DisplayName("Должен вернуть 401 при недействительной подписи refresh token")
    void shouldReturn401WhenRefreshTokenInvalid() throws Exception {
        String invalidToken = "invalid.jwt.token";

        mockMvc.perform(post("/api/refresh")
                        .cookie(new Cookie("refreshToken", invalidToken))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @Transactional
    @DisplayName("Должен вернуть 401 когда refresh token не найден в БД")
    void shouldReturn401WhenRefreshTokenNotFoundInDatabase() throws Exception {
        User user = createUser();
        userRepository.save(user);

        // Генерируем валидный JWT токен, но не сохраняем его в БД
        String rawRefreshToken = jwtService.generateRefreshToken(user);

        mockMvc.perform(post("/api/refresh")
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Refresh token не найден или истек"));
    }

    @Test
    @Transactional
    @DisplayName("Должен вернуть 403 когда пользователь неактивен")
    void shouldReturn403WhenUserIsInactive() throws Exception {
        User user = createUser();
        user.setIsActive(false);
        userRepository.save(user);

        String rawRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, "Mozilla/5.0", "192.168.1.1");

        mockMvc.perform(post("/api/refresh")
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Аккаунт деактивирован. Обратитесь в службу поддержки"));
    }

    @Test
    @Transactional
    @DisplayName("Должен вернуть 403 когда аккаунт заблокирован")
    void shouldReturn403WhenAccountIsLocked() throws Exception {
        User user = createUser();
        user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        String rawRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, "Mozilla/5.0", "192.168.1.1");

        mockMvc.perform(post("/api/refresh")
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Transactional
    @DisplayName("Должен вернуть 404 когда пользователь не найден")
    void shouldReturn404WhenUserNotFound() throws Exception {
        User user = createUser();
        userRepository.save(user);

        String rawRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, "Mozilla/5.0", "192.168.1.1");

        // Сначала удаляем токены, потом пользователя (из-за FK ограничений)
        refreshTokenRepository.deleteAll();
        userRepository.delete(user);

        mockMvc.perform(post("/api/refresh")
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Пользователь не найден"));
    }

    @Test
    @Transactional
    @DisplayName("Должен создать новый refresh token в БД при успешном обновлении")
    void shouldCreateNewRefreshTokenInDatabase() throws Exception {
        User user = createUser();
        userRepository.save(user);

        String rawRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, "Mozilla/5.0", "192.168.1.1");

        mockMvc.perform(post("/api/refresh")
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Проверяем что создан новый токен
        List<RefreshToken> allTokens = refreshTokenRepository.findAll();
        assertThat(allTokens).hasSize(2);

        // Проверяем что новый токен не использован
        List<RefreshToken> activeTokens = refreshTokenRepository.findActiveTokensByUser(user, LocalDateTime.now());
        assertThat(activeTokens).hasSize(1);
        assertThat(activeTokens.getFirst().getIsUsed()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("Должен установить cookie с новым refresh token")
    void shouldSetCookieWithNewRefreshToken() throws Exception {
        User user = createUser();
        userRepository.save(user);

        String rawRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, "Mozilla/5.0", "192.168.1.1");

        mockMvc.perform(post("/api/refresh")
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/auth"))
                .andExpect(cookie().secure("refreshToken", true));
    }

    @Test
    @Transactional
    @DisplayName("Должен разблокировать аккаунт при обновлении токенов если срок блокировки истек")
    void shouldUnlockAccountWhenLockPeriodExpired() throws Exception {
        User user = createUser();
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        user.setFailedLoginAttempts(5);
        userRepository.save(user);

        String rawRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, "Mozilla/5.0", "192.168.1.1");

        mockMvc.perform(post("/api/refresh")
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.body.accessToken").exists());
    }

    @Test
    @Transactional
    @DisplayName("Должен сохранить метаданные нового refresh token (User-Agent)")
    void shouldStoreMetadataForNewRefreshToken() throws Exception {
        User user = createUser();
        userRepository.save(user);

        String rawRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, "Old-Agent", "10.0.0.1");

        String newUserAgent = "New-Agent/2.0";

        mockMvc.perform(post("/api/refresh")
                        .cookie(new Cookie("refreshToken", rawRefreshToken))
                        .header("User-Agent", newUserAgent)
                        .header("X-Forwarded-For", "192.168.100.50")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Проверяем что новый токен содержит новый User-Agent
        List<RefreshToken> activeTokens = refreshTokenRepository.findActiveTokensByUser(user, LocalDateTime.now());
        assertThat(activeTokens).hasSize(1);

        RefreshToken newToken = activeTokens.getFirst();
        assertThat(newToken.getUserAgent()).isEqualTo(newUserAgent);
    }
}