package pyc.lopatuxin.auth.service;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.auth.config.JwtConfig;
import pyc.lopatuxin.auth.dto.request.LoginRequest;
import pyc.lopatuxin.auth.dto.request.RequestHeadersDto;
import pyc.lopatuxin.auth.dto.response.LoginResponse;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.exception.AccountInactiveException;
import pyc.lopatuxin.auth.exception.AccountLockedException;
import pyc.lopatuxin.auth.mapper.UserMapper;
import pyc.lopatuxin.auth.repository.UserRepository;
import pyc.lopatuxin.auth.util.RefreshTokenCookieHelper;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final JwtConfig jwtConfig;
    private final RefreshTokenCookieHelper cookieHelper;
    private final MeterRegistry meterRegistry;
    private final LoginService self;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    public LoginService(UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         JwtService jwtService,
                         RefreshTokenService refreshTokenService,
                         UserMapper userMapper,
                         JwtConfig jwtConfig,
                         RefreshTokenCookieHelper cookieHelper,
                         MeterRegistry meterRegistry,
                         @Lazy LoginService self) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userMapper = userMapper;
        this.jwtConfig = jwtConfig;
        this.cookieHelper = cookieHelper;
        this.meterRegistry = meterRegistry;
        this.self = self;
    }

    // Not @Transactional: every DB access below is either a plain repository read or a call to a
    // self-proxied @Transactional method, each its own short unit committed before the next one
    // starts. That way this request never holds two connections from the pool at once, and never
    // holds one across the bcrypt comparison below (see UserRepository for why the failed-attempts
    // counter is an atomic UPDATE rather than a read-modify-write on this method's User instance).
    public LoginResponse login(LoginRequest request, RequestHeadersDto headers, HttpServletResponse httpResponse) {
        User user = userRepository.findUserByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Неверный email или пароль"));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new AccountInactiveException("Пользователь не активирован");
        }

        LocalDateTime lockedUntil = user.getLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            log.warn("Аккаунт {} заблокирован до {}", user.getEmail(), lockedUntil);
            throw new AccountLockedException("Аккаунт заблокирован");
        }

        if (lockedUntil != null && lockedUntil.isBefore(LocalDateTime.now())) {
            self.resetFailedAttempts(user.getId());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            registerFailedAttempt(user.getId());
            throw new BadCredentialsException("Неверный email или пароль");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        self.completeSuccessfulLogin(user, refreshToken, headers);

        int expiresIn = (int) (jwtConfig.getAccessTokenExpiration() / 1000);
        cookieHelper.setRefreshTokenCookie(httpResponse, refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .user(userMapper.toUserDto(user))
                .build();
    }

    // A failed-attempts update is a side effect of the real response (the 401 below), never the
    // cause of a different one: a pool exhausted of connections, or any other failure here, must
    // not turn an honest 401 into a 500 and must not cost the caller its BadCredentialsException.
    // Narrowed to data-access/transaction failures on purpose: with a 5-connection pool shared by
    // the whole monolith and no rate limiting in front of login(), a flood of wrong-password
    // requests can exhaust the pool, so this exact failure is a realistic attack signal and must
    // not be swallowed by a bare RuntimeException catch that would also hide unrelated bugs.
    // log.error alone is easy to miss under load, so a missed counter write is also counted here,
    // making it visible to metrics/alerting instead of only to whoever happens to read the logs.
    private void registerFailedAttempt(UUID userId) {
        try {
            self.handleFailedLogin(userId);
        } catch (DataAccessException | TransactionException e) {
            log.error("Не удалось обновить счётчик неудачных попыток для пользователя {}", userId, e);
            meterRegistry.counter("auth.login.failed_attempt_write_errors").increment();
        }
    }

    // Atomic UPDATE by id (see UserRepository) instead of a read-modify-write on a detached User
    // instance — two concurrent wrong-password requests must not both compute N+1 from the same
    // snapshot and lose an increment. Reached through the self proxy because a plain in-class call
    // bypasses Spring's AOP proxy, so @Transactional here would otherwise never apply (see
    // TinvestInstrumentResolver for the same pattern).
    @Transactional("authTransactionManager")
    public void handleFailedLogin(UUID userId) {
        userRepository.incrementFailedLoginAttempts(userId);
        userRepository.lockIfThresholdReached(userId,
                LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES), MAX_FAILED_ATTEMPTS);
    }

    @Transactional("authTransactionManager")
    public void resetFailedAttempts(UUID userId) {
        userRepository.resetFailedAttempts(userId);
    }

    // The only write of a successful login — resetting the counter, stamping lastLoginAt and
    // creating the refresh token — in one short transaction, opened only after the bcrypt
    // comparison in login() has already returned.
    @Transactional("authTransactionManager")
    public void completeSuccessfulLogin(User user, String refreshToken, RequestHeadersDto headers) {
        userRepository.resetFailedAttempts(user.getId());
        userRepository.updateLastLoginAt(user.getId(), LocalDateTime.now());
        refreshTokenService.createRefreshToken(user, refreshToken, headers.getUserAgent(), headers.extractIpAddress());
    }
}