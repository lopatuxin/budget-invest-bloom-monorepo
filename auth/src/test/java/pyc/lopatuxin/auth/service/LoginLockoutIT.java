package pyc.lopatuxin.auth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pyc.lopatuxin.auth.AbstractIntegrationTest;
import pyc.lopatuxin.auth.dto.request.ApiRequest;
import pyc.lopatuxin.auth.dto.request.LoginRequest;
import pyc.lopatuxin.auth.entity.User;

import java.time.LocalDateTime;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Deliberately not @Transactional on the test methods: login() is not @Transactional itself —
// every write it makes goes through a short, separately-committed self-proxied transaction (see
// LoginService) — so wrapping a test method in @Transactional would only wrap the test's own
// assertions in a transaction that gets rolled back at the end, without changing what login()
// itself commits. Here every login request commits for real, exactly like a live request, which
// is the only way to see handleFailedLogin's own transaction actually landing in the database.
@DisplayName("LoginService — счётчик неудачных попыток пишется в собственной транзакции, независимой от login()")
class LoginLockoutIT extends AbstractIntegrationTest {

    private static final String EMAIL = "lockout-it@example.com";
    private static final int MAX_FAILED_ATTEMPTS = 5;

    @Autowired
    @Qualifier("authTransactionManager")
    private PlatformTransactionManager transactionManager;

    // A plain @Transactional here would not help: cleanup runs directly on the test instance
    // (never proxied by Spring), and the test methods themselves are deliberately not
    // @Transactional — so this uses a real, self-contained transaction to delete the leftover
    // refresh token and user row between tests.
    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                userRepository.findUserByEmail(EMAIL).ifPresent(user -> {
                    refreshTokenRepository.deleteAllByUser(user);
                    userRepository.delete(user);
                }));
    }

    @Test
    @DisplayName("Счётчик неудачных попыток растёт после каждого провала и переживает откат транзакции login()")
    void failedAttemptsAccumulateAcrossRealTransactionRollbacks() throws Exception {
        userRepository.save(userWith(0, null));

        for (int i = 1; i <= 4; i++) {
            performLogin("wrongPassword").andExpect(status().isUnauthorized());

            User reloaded = userRepository.findUserByEmail(EMAIL).orElseThrow();
            assertThat(reloaded.getFailedLoginAttempts()).isEqualTo(i);
            assertThat(reloaded.getLockedUntil()).isNull();
        }
    }

    @Test
    @DisplayName("Пятая неудачная попытка выставляет блокировку на 15 минут")
    void fifthFailedAttemptLocksAccount() throws Exception {
        userRepository.save(userWith(4, null));

        performLogin("wrongPassword").andExpect(status().isUnauthorized());

        User reloaded = userRepository.findUserByEmail(EMAIL).orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(reloaded.getLockedUntil())
                .isAfter(LocalDateTime.now())
                .isBefore(LocalDateTime.now().plusMinutes(15).plusSeconds(5));
    }

    @Test
    @DisplayName("Вход в заблокированный аккаунт отклоняется с ACCOUNT_LOCKED, даже с верным паролем")
    void loginToLockedAccountIsRejected() throws Exception {
        userRepository.save(userWith(5, LocalDateTime.now().plusMinutes(15)));

        performLogin(TEST_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.body.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("После истечения блокировки счётчик сбрасывается и вход с верным паролем проходит")
    void expiredLockIsLiftedAndCounterReset() throws Exception {
        userRepository.save(userWith(5, LocalDateTime.now().minusMinutes(1)));

        performLogin(TEST_PASSWORD).andExpect(status().isOk());

        User reloaded = userRepository.findUserByEmail(EMAIL).orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();
        assertThat(reloaded.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("Успешный вход после нескольких неудачных попыток обнуляет счётчик")
    void successfulLoginResetsFailedAttempts() throws Exception {
        userRepository.save(userWith(3, null));

        performLogin(TEST_PASSWORD).andExpect(status().isOk());

        User reloaded = userRepository.findUserByEmail(EMAIL).orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();
    }

    // Real Postgres, real concurrent HTTP requests: the bug this guards against only shows up
    // with two genuinely concurrent transactions racing on the same row. Kept below
    // MAX_FAILED_ATTEMPTS on purpose — with fewer attempts than the lock threshold, no request
    // in this burst can ever observe an already-locked account no matter how the pool schedules
    // it, so the assertion is deterministic: every one of the 4 requests reads the counter as 0,
    // fails, and increments it. Read-modify-write on an in-memory snapshot (the bug) loses
    // increments here — several threads read 0 and each writes back 1 — while an atomic
    // UPDATE ... SET failed_login_attempts = failed_login_attempts + 1 forces Postgres to
    // serialize the increments on the row, so the final count is exactly 4.
    @Test
    @DisplayName("Конкурентные неудачные попытки не теряют инкременты — счётчик равен числу попыток, а не меньше")
    void concurrentFailedLogins_counterReflectsEveryAttempt_noLostUpdates() throws Exception {
        userRepository.save(userWith(0, null));

        int attempts = MAX_FAILED_ATTEMPTS - 1;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> attempt = () -> {
                ready.countDown();
                start.await();
                return performLogin("wrongPassword").andReturn().getResponse().getStatus();
            };
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(attempt));
            }
            ready.await();
            start.countDown();

            for (Future<Integer> future : futures) {
                assertThat(future.get(15, TimeUnit.SECONDS)).isEqualTo(401);
            }

            User reloaded = userRepository.findUserByEmail(EMAIL).orElseThrow();
            assertThat(reloaded.getFailedLoginAttempts()).isEqualTo(attempts);
            assertThat(reloaded.getLockedUntil()).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    private User userWith(int failedAttempts, LocalDateTime lockedUntil) {
        User user = createUser();
        user.setEmail(EMAIL);
        user.setFailedLoginAttempts(failedAttempts);
        user.setLockedUntil(lockedUntil);
        return user;
    }

    private org.springframework.test.web.servlet.ResultActions performLogin(String password) throws Exception {
        LoginRequest loginRequest = LoginRequest.builder().email(EMAIL).password(password).build();
        ApiRequest<LoginRequest> apiRequest = ApiRequest.<LoginRequest>builder().data(loginRequest).build();

        return mockMvc.perform(post("/auth/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(apiRequest)));
    }
}
