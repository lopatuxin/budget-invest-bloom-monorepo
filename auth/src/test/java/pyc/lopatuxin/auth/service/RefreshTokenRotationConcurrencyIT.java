package pyc.lopatuxin.auth.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import pyc.lopatuxin.auth.AbstractIntegrationTest;
import pyc.lopatuxin.auth.entity.User;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Real Postgres (Testcontainers), real concurrent HTTP requests — deliberately not @Transactional
// on the test method, same reasoning as RefreshTokenReuseIT: the fix under test is
// RefreshTokenRepository.markUsedIfActive, one atomic conditional UPDATE that only ever lets a
// single concurrent caller win, plus self.deleteAllUserTokens's own REQUIRES_NEW commit. Both must
// be observed against a real database under genuine concurrency; a mocked repository could not
// show two transactions actually racing on the same row.
@DisplayName("RefreshTokenService — гонка при одновременном предъявлении одного и того же refresh token")
class RefreshTokenRotationConcurrencyIT extends AbstractIntegrationTest {

    private static final String EMAIL = "refresh-race-it@example.com";

    @AfterEach
    void cleanUp() {
        userRepository.findUserByEmail(EMAIL).ifPresent(user -> {
            refreshTokenRepository.deleteAllByUser(user);
            userRepository.delete(user);
        });
    }

    @Test
    @DisplayName("Два потока предъявляют один и тот же refresh token одновременно — ровно одна успешная ротация, второй получает REFRESH_TOKEN_REUSED, а не обе проходят")
    void concurrentPresentationOfSameToken_exactlyOneRotationSucceeds_otherRaisesReuseAlarm() throws Exception {
        User user = createUser();
        user.setEmail(EMAIL);
        userRepository.save(user);

        String rawRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, "Test-Agent", "127.0.0.1");

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<MvcResult> attempt = () -> {
                ready.countDown();
                start.await();
                return performRefresh(rawRefreshToken).andReturn();
            };
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(attempt));
            }
            ready.await();
            start.countDown();

            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }

            long successes = results.stream()
                    .filter(r -> r.getResponse().getStatus() == 200)
                    .count();
            long reuseRejections = results.stream()
                    .filter(r -> r.getResponse().getStatus() == 403)
                    .count();

            assertThat(successes)
                    .as("exactly one of the two concurrent presentations must rotate the token")
                    .isEqualTo(1);
            assertThat(reuseRejections)
                    .as("the losing presentation must be rejected as reuse, not silently succeed too")
                    .isEqualTo(1);

            String reuseBody = results.stream()
                    .filter(r -> r.getResponse().getStatus() == 403)
                    .findFirst().orElseThrow()
                    .getResponse().getContentAsString();
            assertThat(reuseBody).contains("REFRESH_TOKEN_REUSED");

            // The alarm actually fired: the whole user's session set was wiped, including the
            // winning presentation's freshly-rotated token — not just the losing request's own row.
            assertThat(refreshTokenRepository.findAll().stream()
                    .noneMatch(token -> token.getUser().getId().equals(user.getId())))
                    .as("reuse detection must delete every session for this user, winner included")
                    .isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private ResultActions performRefresh(String rawRefreshToken) throws Exception {
        return mockMvc.perform(post("/auth/api/refresh")
                .cookie(new Cookie("refreshToken", rawRefreshToken))
                .header("User-Agent", "Test-Agent")
                .header("X-Forwarded-For", "127.0.0.1")
                .contentType(MediaType.APPLICATION_JSON));
    }
}
