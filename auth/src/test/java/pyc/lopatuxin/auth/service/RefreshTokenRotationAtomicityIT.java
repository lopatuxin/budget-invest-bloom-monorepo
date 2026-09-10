package pyc.lopatuxin.auth.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pyc.lopatuxin.auth.AbstractIntegrationTest;
import pyc.lopatuxin.auth.dto.request.RequestHeadersDto;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.util.RefreshTokenHasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Real Postgres (Testcontainers), deliberately not @Transactional on the test method — same
// reasoning as RefreshTokenReuseIT and RefreshTokenRotationConcurrencyIT: the fix under test is
// RefreshTokenService.rotateToken's own transaction boundary. A test method wrapped in its own
// @Transactional would roll back everything at the end regardless of whether rotateToken's UPDATE
// and INSERT are actually bundled together, so it would prove nothing about the fix.
@DisplayName("RefreshTokenService — атомарность ротации: сбой при создании нового токена не оставляет старый помеченным использованным")
class RefreshTokenRotationAtomicityIT extends AbstractIntegrationTest {

    private static final String EMAIL = "refresh-atomicity-it@example.com";

    @Autowired
    private RefreshTokenHasher tokenHasher;

    // Uses the service's own @Transactional deleteAllUserTokens rather than calling the repository's
    // derived deleteAllByUser directly: unlike RefreshTokenReuseIT/RefreshTokenRotationConcurrencyIT,
    // this test's own reuse-detection path is never triggered, so real rows are always still there
    // to delete — and a derived delete-by query with no surrounding transaction fails outright.
    @AfterEach
    void cleanUp() {
        userRepository.findUserByEmail(EMAIL).ifPresent(user -> {
            refreshTokenService.deleteAllUserTokens(user);
            userRepository.delete(user);
        });
    }

    @Test
    @DisplayName("Нарушение уникальности token_hash при вставке нового токена откатывает и отметку старого использованным — повторный запрос тем же старым токеном ротирует как обычно, а не сносит сессии")
    void failureWhileCreatingNewToken_rollsBackOldTokenMarking_soRetryStillRotatesNormally() throws Exception {
        User user = createUser();
        user.setEmail(EMAIL);
        userRepository.save(user);

        String oldRawToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, oldRawToken, "Test-Agent", "127.0.0.1");

        // A pre-existing row whose hash the "new" token below will collide with, standing in for
        // whatever real-world failure (DB error, timeout, pool exhaustion) could interrupt
        // rotateToken between its UPDATE and its INSERT.
        String collidingRawToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, collidingRawToken, "Test-Agent", "127.0.0.1");

        String oldTokenHash = tokenHasher.hash(oldRawToken);
        RequestHeadersDto headers = RequestHeadersDto.builder()
                .userAgent("Test-Agent")
                .xForwardedFor("127.0.0.1")
                .build();

        Throwable failure = catchThrowable(
                () -> refreshTokenService.rotateToken(user, oldTokenHash, collidingRawToken, headers));

        assertThat(failure)
                .as("inserting the new token must fail on the unique token_hash constraint")
                .isInstanceOf(RuntimeException.class);
        assertThat(rootCauseMessage(failure))
                .as("the failure must actually be the unique constraint, not some unrelated bug")
                .containsIgnoringCase("token_hash");

        assertThat(refreshTokenService.findValidToken(oldRawToken, user))
                .as("the old token's isUsed flag must have rolled back together with the failed insert, " +
                        "not been left committed on its own")
                .isNotNull();

        // Proves the user is not stuck: presenting the very same old token again rotates normally
        // instead of being treated as a replay of an already-used token.
        performRefresh(oldRawToken).andExpect(status().isOk());
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }

    private ResultActions performRefresh(String rawRefreshToken) throws Exception {
        return mockMvc.perform(post("/auth/api/refresh")
                .cookie(new Cookie("refreshToken", rawRefreshToken))
                .header("User-Agent", "Test-Agent")
                .header("X-Forwarded-For", "127.0.0.1")
                .contentType(MediaType.APPLICATION_JSON));
    }
}
