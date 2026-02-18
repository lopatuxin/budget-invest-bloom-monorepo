package pyc.lopatuxin.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pyc.lopatuxin.auth.config.JwtConfig;
import pyc.lopatuxin.auth.entity.RefreshToken;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.repository.RefreshTokenRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    private PasswordEncoder tokenEncoder;

    @Mock
    private JwtConfig jwtConfig;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private static final Long REFRESH_TOKEN_EXPIRATION = 604800000L; // 7 дней в миллисекундах
    private static final String RAW_TOKEN = "raw-token-value";
    private static final String ENCODED_TOKEN = "encoded-token-hash";
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
        lenient().when(tokenEncoder.encode(anyString())).thenReturn(ENCODED_TOKEN);
    }

    @Test
    @DisplayName("Должен успешно создать и сохранить refresh токен")
    void shouldCreateAndSaveRefreshToken() {
        refreshTokenService.createRefreshToken(testUser, RAW_TOKEN, USER_AGENT, IP_ADDRESS);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken).isNotNull();
        assertThat(savedToken.getUser()).isEqualTo(testUser);
        assertThat(savedToken.getTokenHash()).isEqualTo(ENCODED_TOKEN);
        assertThat(savedToken.getIsUsed()).isFalse();
        assertThat(savedToken.getUserAgent()).isEqualTo(USER_AGENT);
        assertThat(savedToken.getIpAddress()).isEqualTo(IP_ADDRESS);
        assertThat(savedToken.getCreatedAt()).isNotNull();
        assertThat(savedToken.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("Должен закодировать токен перед сохранением")
    void shouldEncodeTokenBeforeSaving() {
        refreshTokenService.createRefreshToken(testUser, RAW_TOKEN, USER_AGENT, IP_ADDRESS);

        verify(tokenEncoder).encode(RAW_TOKEN);
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

    @Test
    @DisplayName("Должен найти валидный токен")
    void shouldFindValidToken() {
        String tokenHash = "stored-token-hash";
        RefreshToken expectedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .isUsed(false)
                .build();

        when(refreshTokenRepository.findActiveTokensByUser(eq(testUser), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(expectedToken));
        when(tokenEncoder.matches(RAW_TOKEN, tokenHash)).thenReturn(true);

        RefreshToken result = refreshTokenService.findValidToken(RAW_TOKEN, testUser);

        assertThat(result)
                .isNotNull()
                .isEqualTo(expectedToken);
        verify(refreshTokenRepository).findActiveTokensByUser(eq(testUser), any(LocalDateTime.class));
        verify(tokenEncoder).matches(RAW_TOKEN, tokenHash);
    }

    @Test
    @DisplayName("Должен вернуть null если токен не найден")
    void shouldReturnNullWhenTokenNotFound() {
        when(refreshTokenRepository.findActiveTokensByUser(eq(testUser), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        RefreshToken result = refreshTokenService.findValidToken(RAW_TOKEN, testUser);

        assertThat(result).isNull();
        verify(refreshTokenRepository).findActiveTokensByUser(eq(testUser), any(LocalDateTime.class));
        verify(tokenEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Должен вернуть null если токен не совпадает")
    void shouldReturnNullWhenTokenDoesNotMatch() {
        RefreshToken storedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("different-token-hash")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .isUsed(false)
                .build();

        when(refreshTokenRepository.findActiveTokensByUser(eq(testUser), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(storedToken));
        when(tokenEncoder.matches(RAW_TOKEN, "different-token-hash")).thenReturn(false);

        RefreshToken result = refreshTokenService.findValidToken(RAW_TOKEN, testUser);

        assertThat(result).isNull();
        verify(tokenEncoder).matches(RAW_TOKEN, "different-token-hash");
    }

    @Test
    @DisplayName("Должен найти правильный токен среди нескольких")
    void shouldFindCorrectTokenAmongMultiple() {
        RefreshToken token1 = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash1")
                .build();

        RefreshToken token2 = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash2")
                .build();

        RefreshToken token3 = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash3")
                .build();

        List<RefreshToken> tokens = Arrays.asList(token1, token2, token3);

        when(refreshTokenRepository.findActiveTokensByUser(eq(testUser), any(LocalDateTime.class)))
                .thenReturn(tokens);
        when(tokenEncoder.matches(RAW_TOKEN, "hash1")).thenReturn(false);
        when(tokenEncoder.matches(RAW_TOKEN, "hash2")).thenReturn(true);

        RefreshToken result = refreshTokenService.findValidToken(RAW_TOKEN, testUser);

        assertThat(result).isEqualTo(token2);
        verify(tokenEncoder).matches(RAW_TOKEN, "hash1");
        verify(tokenEncoder).matches(RAW_TOKEN, "hash2");
        verify(tokenEncoder, never()).matches(RAW_TOKEN, "hash3");
    }

    @Test
    @DisplayName("Должен пометить токен как использованный")
    void shouldMarkTokenAsUsed() {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(ENCODED_TOKEN)
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
                .tokenHash(ENCODED_TOKEN)
                .isUsed(false)
                .build();

        refreshTokenService.markAsUsed(token);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        assertThat(tokenCaptor.getValue().getIsUsed()).isTrue();
    }

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