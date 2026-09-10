package pyc.lopatuxin.auth.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pyc.lopatuxin.auth.AbstractIntegrationTest;
import pyc.lopatuxin.auth.entity.RefreshToken;
import pyc.lopatuxin.auth.entity.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Deliberately not @Transactional on the test methods, same reasoning as LoginLockoutIT: the fix
// under test is a self-proxied REQUIRES_NEW transaction (RefreshTokenService.deleteAllUserTokens)
// that must commit for real regardless of the surrounding transaction rolling back. A test method
// wrapped in its own @Transactional would still let REQUIRES_NEW commit independently — so the
// deletion would look like it survived even on the old, buggy code, because the test's own
// rollback only ever undid the test's assertions, never the REQUIRES_NEW commit it is trying to
// verify. Only a real, uncontrolled request — exactly like production — proves anything here.
@DisplayName("RefreshTokenService — удаление токенов при обнаружении кражи переживает откат refreshTokens()")
class RefreshTokenReuseIT extends AbstractIntegrationTest {

    private static final String EMAIL = "refresh-reuse-it@example.com";

    @AfterEach
    void cleanUp() {
        userRepository.findUserByEmail(EMAIL).ifPresent(user -> {
            refreshTokenRepository.deleteAllByUser(user);
            userRepository.delete(user);
        });
    }

    @Test
    @DisplayName("Повторное предъявление уже использованного refresh token удаляет из БД все токены пользователя, а не откатывает удаление")
    void reusingAlreadyRotatedToken_deletesAllUserTokensForReal() throws Exception {
        User user = createUser();
        user.setEmail(EMAIL);
        userRepository.save(user);

        String rawRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, "Test-Agent", "127.0.0.1");

        // First refresh: rotates the token — the old row becomes isUsed=true, a new one is created.
        performRefresh(rawRefreshToken).andExpect(status().isOk());

        assertThat(tokensFor(user)).hasSize(2);

        // Replaying the same, now-consumed raw token simulates an attacker who stole it earlier
        // and presents it after the legitimate client already rotated it.
        performRefresh(rawRefreshToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Refresh token уже был использован. Возможна компрометация сессии. Требуется повторный вход"));

        assertThat(tokensFor(user))
                .as("both the consumed original and the freshly-rotated token must be gone, not just rolled back")
                .isEmpty();
    }

    private List<RefreshToken> tokensFor(User user) {
        return refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(user.getId()))
                .toList();
    }

    private ResultActions performRefresh(String rawRefreshToken) throws Exception {
        return mockMvc.perform(post("/auth/api/refresh")
                .cookie(new Cookie("refreshToken", rawRefreshToken))
                .header("User-Agent", "Test-Agent")
                .header("X-Forwarded-For", "127.0.0.1")
                .contentType(MediaType.APPLICATION_JSON));
    }
}
