package pyc.lopatuxin.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.auth.config.JwtConfig;
import pyc.lopatuxin.auth.entity.RefreshToken;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.repository.RefreshTokenRepository;
import pyc.lopatuxin.auth.util.RefreshTokenHasher;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceUnitTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenHasher tokenHasher;

    @Mock
    private JwtConfig jwtConfig;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private static final Long REFRESH_TOKEN_EXPIRATION = 604800000L; // 7 дней в миллисекундах
    private static final String RAW_TOKEN = "raw-token-value";
    private static final String TOKEN_HASH = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String IP_ADDRESS = "192.168.1.1";

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .username("testuser")
                .build();

        lenient().when(jwtConfig.getRefreshTokenExpiration()).thenReturn(REFRESH_TOKEN_EXPIRATION);
        lenient().when(tokenHasher.hash(anyString())).thenReturn(TOKEN_HASH);
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
        verify(tokenHasher).hash(RAW_TOKEN);
        verify(refreshTokenRepository).findActiveByUserAndTokenHash(
                eq(testUser), eq(TOKEN_HASH), any(LocalDateTime.class));
    }

    // --- createRefreshToken ---

    @Test
    @DisplayName("Должен успешно создать и сохранить refresh токен")
    void shouldCreateAndSaveRefreshToken() {
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

    @Test
    @DisplayName("Должен захешировать токен через tokenHasher перед сохранением")
    void shouldHashTokenBeforeSaving() {
        refreshTokenService.createRefreshToken(testUser, RAW_TOKEN, USER_AGENT, IP_ADDRESS);

        verify(tokenHasher).hash(RAW_TOKEN);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Должен установить корректное время истечения токена")
    void shouldSetCorrectExpirationTime() {
        LocalDateTime beforeCreation = LocalDateTime.now();

        refreshTokenService.createRefreshToken(testUser, RAW_TOKEN, USER_AGENT, IP_ADDRESS);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        RefreshToken savedToken = tokenCaptor.getValue();
        LocalDateTime expectedExpiration = beforeCreation.plusSeconds(REFRESH_TOKEN_EXPIRATION / 1000);

        assertThat(savedToken.getExpiresAt())
                .isAfter(expectedExpiration.minusSeconds(1))
                .isBefore(expectedExpiration.plusSeconds(1));
    }

    // --- markAsUsed ---

    @Test
    @DisplayName("Должен пометить токен как использованный")
    void shouldMarkTokenAsUsed() {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(TOKEN_HASH)
                .isUsed(false)
                .build();

        refreshTokenService.markAsUsed(token);

        assertThat(token.getIsUsed()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    @DisplayName("Должен сохранить токен после пометки как использованный")
    void shouldSaveTokenAfterMarkingAsUsed() {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(TOKEN_HASH)
                .isUsed(false)
                .build();

        refreshTokenService.markAsUsed(token);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        assertThat(tokenCaptor.getValue().getIsUsed()).isTrue();
    }

    // --- deleteAllUserTokens ---

    @Test
    @DisplayName("Должен удалить все токены пользователя")
    void shouldDeleteAllUserTokens() {
        refreshTokenService.deleteAllUserTokens(testUser);

        verify(refreshTokenRepository).deleteAllByUser(testUser);
    }

    @Test
    @DisplayName("Должен вызвать метод удаления один раз")
    void shouldCallDeleteMethodOnce() {
        refreshTokenService.deleteAllUserTokens(testUser);

        verify(refreshTokenRepository, times(1)).deleteAllByUser(testUser);
    }
}
