package pyc.lopatuxin.auth.service;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import pyc.lopatuxin.auth.config.JwtConfig;
import pyc.lopatuxin.auth.dto.request.RequestHeadersDto;
import pyc.lopatuxin.auth.entity.RefreshToken;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.exception.InvalidRefreshTokenException;
import pyc.lopatuxin.auth.exception.RefreshTokenReusedException;
import pyc.lopatuxin.auth.repository.RefreshTokenRepository;
import pyc.lopatuxin.auth.repository.UserRepository;
import pyc.lopatuxin.auth.util.RefreshTokenCookieHelper;
import pyc.lopatuxin.auth.util.RefreshTokenHasher;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// self is mocked, not the real Spring AOP proxy, throughout this class: rotateToken is
// @Transactional and only meaningfully exercised through the real proxy in an IT (see
// RefreshTokenReuseIT, RefreshTokenRotationConcurrencyIT and RefreshTokenRotationAtomicityIT).
// These tests instead pin down refreshTokens()'s own orchestration logic — which branch it takes
// and what it calls next — given each possible outcome of self.rotateToken, plus rotateToken's own
// conditional logic in isolation (mocking the repositories it calls directly).
@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceUnitTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenCookieHelper cookieHelper;

    @Mock
    private RefreshTokenHasher tokenHasher;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private RefreshTokenService self;

    private RefreshTokenService refreshTokenService;

    private static final Long REFRESH_TOKEN_EXPIRATION = 604800000L; // 7 дней в миллисекундах
    private static final String RAW_TOKEN = "raw-token-value";
    private static final String TOKEN_HASH = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String IP_ADDRESS = "192.168.1.1";

    private User testUser;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, userRepository, jwtService,
                cookieHelper, tokenHasher, jwtConfig, self);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .username("testuser")
                .build();
    }

    // --- findValidToken ---

    @Test
    @DisplayName("findValidToken должен вернуть токен, когда репозиторий нашёл запись по hash")
    void findValidToken_shouldReturnToken_whenRepositoryFoundByHash() {
        RefreshToken expectedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(TOKEN_HASH)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .isUsed(false)
                .build();

        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findActiveByUserAndTokenHash(
                eq(testUser), eq(TOKEN_HASH), any(LocalDateTime.class)))
                .thenReturn(Optional.of(expectedToken));

        RefreshToken result = refreshTokenService.findValidToken(RAW_TOKEN, testUser);

        assertThat(result)
                .isNotNull()
                .isEqualTo(expectedToken);
        verify(tokenHasher).hash(RAW_TOKEN);
        verify(refreshTokenRepository).findActiveByUserAndTokenHash(
                eq(testUser), eq(TOKEN_HASH), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("findValidToken должен вернуть null, если репозиторий вернул Optional.empty()")
    void findValidToken_shouldReturnNull_whenRepositoryReturnsEmpty() {
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findActiveByUserAndTokenHash(
                eq(testUser), eq(TOKEN_HASH), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        RefreshToken result = refreshTokenService.findValidToken(RAW_TOKEN, testUser);

        assertThat(result).isNull();
    }

    // --- createRefreshToken ---

    @Test
    @DisplayName("Должен успешно создать и сохранить refresh токен")
    void shouldCreateAndSaveRefreshToken() {
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(jwtConfig.getRefreshTokenExpiration()).thenReturn(REFRESH_TOKEN_EXPIRATION);

        refreshTokenService.createRefreshToken(testUser, RAW_TOKEN, USER_AGENT, IP_ADDRESS);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken).isNotNull();
        assertThat(savedToken.getUser()).isEqualTo(testUser);
        assertThat(savedToken.getTokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(savedToken.getIsUsed()).isFalse();
        assertThat(savedToken.getUserAgent()).isEqualTo(USER_AGENT);
        assertThat(savedToken.getIpAddress()).isEqualTo(IP_ADDRESS);
        assertThat(savedToken.getCreatedAt()).isNotNull();
        assertThat(savedToken.getExpiresAt()).isNotNull();
    }

    // --- deleteAllUserTokens ---

    @Test
    @DisplayName("Должен удалить все токены пользователя")
    void shouldDeleteAllUserTokens() {
        refreshTokenService.deleteAllUserTokens(testUser);

        verify(refreshTokenRepository, times(1)).deleteAllByUser(testUser);
    }

    // --- rotateToken ---

    @Test
    @DisplayName("rotateToken должен создать новый токен и обновить lastLoginAt, когда UPDATE затронул хотя бы одну строку")
    void rotateToken_shouldCreateNewTokenAndUpdateLastLogin_whenRowsUpdated() {
        when(refreshTokenRepository.markUsedIfActive(eq(testUser), eq(TOKEN_HASH), any(LocalDateTime.class)))
                .thenReturn(1);
        when(tokenHasher.hash("new-refresh-token")).thenReturn("new-token-hash");
        when(jwtConfig.getRefreshTokenExpiration()).thenReturn(REFRESH_TOKEN_EXPIRATION);

        RequestHeadersDto headers = RequestHeadersDto.builder().userAgent(USER_AGENT).xForwardedFor(IP_ADDRESS).build();

        int updatedRows = refreshTokenService.rotateToken(testUser, TOKEN_HASH, "new-refresh-token", headers);

        assertThat(updatedRows).isEqualTo(1);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(userRepository).updateLastLoginAt(eq(testUser.getId()), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("rotateToken не должен создавать новый токен, когда UPDATE не затронул ни одной строки")
    void rotateToken_shouldNotCreateNewToken_whenNoRowsUpdated() {
        when(refreshTokenRepository.markUsedIfActive(eq(testUser), eq(TOKEN_HASH), any(LocalDateTime.class)))
                .thenReturn(0);

        RequestHeadersDto headers = RequestHeadersDto.builder().userAgent(USER_AGENT).xForwardedFor(IP_ADDRESS).build();

        int updatedRows = refreshTokenService.rotateToken(testUser, TOKEN_HASH, "new-refresh-token", headers);

        assertThat(updatedRows).isZero();
        verify(refreshTokenRepository, never()).save(any());
        verify(userRepository, never()).updateLastLoginAt(any(), any());
    }

    // Guards against the "0 or 2 both mean reuse" bug: an UPDATE that (through some future defect
    // upstream, e.g. a missing uniqueness guarantee) affects more than one row must still be
    // treated as a successful rotation, not misclassified as reuse the way an == 1 check would.
    @Test
    @DisplayName("rotateToken должен считать ротацию успешной и при affected rows больше единицы")
    void rotateToken_shouldTreatMoreThanOneAffectedRowAsSuccess() {
        when(refreshTokenRepository.markUsedIfActive(eq(testUser), eq(TOKEN_HASH), any(LocalDateTime.class)))
                .thenReturn(2);
        when(tokenHasher.hash("new-refresh-token")).thenReturn("new-token-hash");
        when(jwtConfig.getRefreshTokenExpiration()).thenReturn(REFRESH_TOKEN_EXPIRATION);

        RequestHeadersDto headers = RequestHeadersDto.builder().userAgent(USER_AGENT).xForwardedFor(IP_ADDRESS).build();

        int updatedRows = refreshTokenService.rotateToken(testUser, TOKEN_HASH, "new-refresh-token", headers);

        assertThat(updatedRows).isEqualTo(2);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // --- refreshTokens ---

    @Test
    @DisplayName("Успешная ротация: отметка старого токена и создание нового происходят одной короткой транзакцией через self-прокси")
    void refreshTokens_shouldRotateThroughSelfProxy_whenTokenWasActive() {
        when(jwtService.extractUserId(RAW_TOKEN)).thenReturn(testUser.getId());
        when(userRepository.findByIdWithRoles(testUser.getId())).thenReturn(Optional.of(testUser));
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(jwtService.generateRefreshToken(testUser)).thenReturn("new-refresh-token");
        when(jwtService.generateAccessToken(testUser)).thenReturn("new-access-token");
        when(jwtConfig.getAccessTokenExpiration()).thenReturn(900000L);

        RequestHeadersDto headers = RequestHeadersDto.builder().userAgent(USER_AGENT).xForwardedFor(IP_ADDRESS).build();
        when(self.rotateToken(testUser, TOKEN_HASH, "new-refresh-token", headers)).thenReturn(1);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);

        refreshTokenService.refreshTokens(RAW_TOKEN, headers, httpResponse);

        verify(self).rotateToken(testUser, TOKEN_HASH, "new-refresh-token", headers);
        verify(cookieHelper).setRefreshTokenCookie(httpResponse, "new-refresh-token");
        verify(self, never()).deleteAllUserTokens(any());
        verify(cookieHelper, never()).clearRefreshTokenCookie(any());
    }

    @Test
    @DisplayName("Токен не найден и не истёк одновременно с использованием: обнаружение повтора удаляет токены через self-прокси, чистит cookie и бросает RefreshTokenReusedException")
    void refreshTokens_shouldDeleteAllTokensAndClearCookie_whenTokenAlreadyUsed() {
        when(jwtService.extractUserId(RAW_TOKEN)).thenReturn(testUser.getId());
        when(userRepository.findByIdWithRoles(testUser.getId())).thenReturn(Optional.of(testUser));
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(jwtService.generateRefreshToken(testUser)).thenReturn("new-refresh-token");
        RequestHeadersDto headers = RequestHeadersDto.builder().build();
        when(self.rotateToken(testUser, TOKEN_HASH, "new-refresh-token", headers)).thenReturn(0);
        when(refreshTokenRepository.existsByUserAndTokenHashAndExpiresAtAfterAndIsUsedTrue(
                eq(testUser), eq(TOKEN_HASH), any(LocalDateTime.class)))
                .thenReturn(true);

        HttpServletResponse httpResponse = mock(HttpServletResponse.class);

        assertThatThrownBy(() -> refreshTokenService.refreshTokens(RAW_TOKEN, headers, httpResponse))
                .isInstanceOf(RefreshTokenReusedException.class);

        verify(self).deleteAllUserTokens(testUser);
        verify(cookieHelper).clearRefreshTokenCookie(httpResponse);
    }

    @Test
    @DisplayName("Токен не найден в БД или истёк (не был использован): бросает InvalidRefreshTokenException, сессии не сносятся")
    void refreshTokens_shouldThrowInvalid_whenTokenNeverExistedOrExpired() {
        when(jwtService.extractUserId(RAW_TOKEN)).thenReturn(testUser.getId());
        when(userRepository.findByIdWithRoles(testUser.getId())).thenReturn(Optional.of(testUser));
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(jwtService.generateRefreshToken(testUser)).thenReturn("new-refresh-token");
        RequestHeadersDto headers = RequestHeadersDto.builder().build();
        when(self.rotateToken(testUser, TOKEN_HASH, "new-refresh-token", headers)).thenReturn(0);
        when(refreshTokenRepository.existsByUserAndTokenHashAndExpiresAtAfterAndIsUsedTrue(
                eq(testUser), eq(TOKEN_HASH), any(LocalDateTime.class)))
                .thenReturn(false);

        HttpServletResponse httpResponse = mock(HttpServletResponse.class);

        assertThatThrownBy(() -> refreshTokenService.refreshTokens(RAW_TOKEN, headers, httpResponse))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token не найден или истек");

        verify(self, never()).deleteAllUserTokens(any());
        verify(cookieHelper, never()).clearRefreshTokenCookie(any());
    }

    @Test
    @DisplayName("Сбой удаления сессий при компрометации — best-effort: логируется, но клиент всё равно получает RefreshTokenReusedException, а не сырую ошибку")
    void refreshTokens_reuseDetected_sessionWipeFailure_isSwallowed_andReusedExceptionStillThrown() {
        when(jwtService.extractUserId(RAW_TOKEN)).thenReturn(testUser.getId());
        when(userRepository.findByIdWithRoles(testUser.getId())).thenReturn(Optional.of(testUser));
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(jwtService.generateRefreshToken(testUser)).thenReturn("new-refresh-token");
        RequestHeadersDto headers = RequestHeadersDto.builder().build();
        when(self.rotateToken(testUser, TOKEN_HASH, "new-refresh-token", headers)).thenReturn(0);
        when(refreshTokenRepository.existsByUserAndTokenHashAndExpiresAtAfterAndIsUsedTrue(
                eq(testUser), eq(TOKEN_HASH), any(LocalDateTime.class)))
                .thenReturn(true);
        doThrow(new QueryTimeoutException("db timeout")).when(self).deleteAllUserTokens(testUser);

        HttpServletResponse httpResponse = mock(HttpServletResponse.class);

        assertThatThrownBy(() -> refreshTokenService.refreshTokens(RAW_TOKEN, headers, httpResponse))
                .isInstanceOf(RefreshTokenReusedException.class);

        verify(cookieHelper).clearRefreshTokenCookie(httpResponse);
    }

    @Test
    @DisplayName("Исчерпание пула соединений (TransactionException) при удалении сессий тоже не подменяет RefreshTokenReusedException")
    void refreshTokens_reuseDetected_connectionPoolExhaustionDuringWipe_isSwallowed() {
        when(jwtService.extractUserId(RAW_TOKEN)).thenReturn(testUser.getId());
        when(userRepository.findByIdWithRoles(testUser.getId())).thenReturn(Optional.of(testUser));
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(jwtService.generateRefreshToken(testUser)).thenReturn("new-refresh-token");
        RequestHeadersDto headers = RequestHeadersDto.builder().build();
        when(self.rotateToken(testUser, TOKEN_HASH, "new-refresh-token", headers)).thenReturn(0);
        when(refreshTokenRepository.existsByUserAndTokenHashAndExpiresAtAfterAndIsUsedTrue(
                eq(testUser), eq(TOKEN_HASH), any(LocalDateTime.class)))
                .thenReturn(true);
        doThrow(new CannotCreateTransactionException("pool exhausted")).when(self).deleteAllUserTokens(testUser);

        HttpServletResponse httpResponse = mock(HttpServletResponse.class);

        assertThatThrownBy(() -> refreshTokenService.refreshTokens(RAW_TOKEN, headers, httpResponse))
                .isInstanceOf(RefreshTokenReusedException.class);
    }
}
