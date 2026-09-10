package pyc.lopatuxin.auth.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.CannotCreateTransactionException;
import pyc.lopatuxin.auth.config.JwtConfig;
import pyc.lopatuxin.auth.dto.request.LoginRequest;
import pyc.lopatuxin.auth.dto.request.RequestHeadersDto;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.mapper.UserMapper;
import pyc.lopatuxin.auth.repository.UserRepository;
import pyc.lopatuxin.auth.util.RefreshTokenCookieHelper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// self is mocked, not the real Spring AOP proxy: these tests exercise only registerFailedAttempt's
// exception-narrowing, which does not depend on handleFailedLogin's own @Transactional behavior.
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginService — сужение перехвата вокруг записи счётчика неудачных попыток")
class LoginServiceUnitTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtConfig jwtConfig;
    @Mock
    private RefreshTokenCookieHelper cookieHelper;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private LoginService self;
    @Mock
    private Counter counter;

    private LoginService loginService;

    private static final String EMAIL = "test@example.com";
    private static final String WRONG_PASSWORD = "wrongPassword";

    private User user;
    private LoginRequest request;
    private RequestHeadersDto headers;
    private HttpServletResponse httpResponse;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(userRepository, passwordEncoder, jwtService, refreshTokenService,
                userMapper, jwtConfig, cookieHelper, meterRegistry, self);

        user = User.builder().id(UUID.randomUUID()).email(EMAIL).passwordHash("hash").build();
        request = LoginRequest.builder().email(EMAIL).password(WRONG_PASSWORD).build();
        headers = RequestHeadersDto.builder().build();
        httpResponse = mock(HttpServletResponse.class);

        when(userRepository.findUserByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(WRONG_PASSWORD, "hash")).thenReturn(false);
    }

    @Test
    @DisplayName("Сбой доступа к данным при записи счётчика не подменяет 401 на 500 и учитывается в метрике")
    void dataAccessFailureDuringCounterWrite_isSwallowed_andCounted() {
        doThrow(new QueryTimeoutException("db timeout")).when(self).handleFailedLogin(eq(user.getId()));
        when(meterRegistry.counter(anyString())).thenReturn(counter);

        assertThatThrownBy(() -> loginService.login(request, headers, httpResponse))
                .isInstanceOf(BadCredentialsException.class);

        verify(counter).increment();
    }

    @Test
    @DisplayName("Исчерпание пула соединений при записи счётчика (TransactionException) тоже не подменяет 401 на 500")
    void connectionPoolExhaustionDuringCounterWrite_isSwallowed_andCounted() {
        doThrow(new CannotCreateTransactionException("pool exhausted")).when(self).handleFailedLogin(eq(user.getId()));
        when(meterRegistry.counter(anyString())).thenReturn(counter);

        assertThatThrownBy(() -> loginService.login(request, headers, httpResponse))
                .isInstanceOf(BadCredentialsException.class);

        verify(counter).increment();
    }

    @Test
    @DisplayName("Ошибка, не относящаяся к доступу к данным, не проглатывается сузившимся catch")
    void unrelatedRuntimeExceptionDuringCounterWrite_propagates() {
        doThrow(new IllegalStateException("programming error")).when(self).handleFailedLogin(eq(user.getId()));

        assertThatThrownBy(() -> loginService.login(request, headers, httpResponse))
                .isInstanceOf(IllegalStateException.class);
    }
}
