package pyc.lopatuxin.auth.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.auth.config.RefreshTokenConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenHasher")
class RefreshTokenHasherTest {

    private static final String PEPPER = "test-pepper-32bytes-padded-xxxxx";
    private static final String OTHER_PEPPER = "other-pepper-32bytes-padded-xxxx";
    private static final String RAW_TOKEN = "eyJhbGciOiJIUzI1NiJ9.somePayload.signature";

    @Mock
    private RefreshTokenConfig refreshTokenConfig;

    @InjectMocks
    private RefreshTokenHasher refreshTokenHasher;

    @Test
    @DisplayName("Должен возвращать одинаковый хеш при многократном вызове с одним и тем же входом")
    void shouldBeDeterministic() {
        when(refreshTokenConfig.getPepper()).thenReturn(PEPPER);

        String hash1 = refreshTokenHasher.hash(RAW_TOKEN);
        String hash2 = refreshTokenHasher.hash(RAW_TOKEN);
        String hash3 = refreshTokenHasher.hash(RAW_TOKEN);

        assertThat(hash1)
                .isEqualTo(hash2)
                .isEqualTo(hash3);
    }

    @Test
    @DisplayName("Разные pepper должны давать разные хеши для одного входа")
    void differentPeppersShouldProduceDifferentHashes() {
        when(refreshTokenConfig.getPepper()).thenReturn(PEPPER);
        String hashWithPepper1 = refreshTokenHasher.hash(RAW_TOKEN);

        when(refreshTokenConfig.getPepper()).thenReturn(OTHER_PEPPER);
        String hashWithPepper2 = refreshTokenHasher.hash(RAW_TOKEN);

        assertThat(hashWithPepper1).isNotEqualTo(hashWithPepper2);
    }

    @Test
    @DisplayName("matches должен возвращать true для правильного токена")
    void matchesShouldReturnTrueForCorrectToken() {
        when(refreshTokenConfig.getPepper()).thenReturn(PEPPER);

        String storedHash = refreshTokenHasher.hash(RAW_TOKEN);
        boolean result = refreshTokenHasher.matches(RAW_TOKEN, storedHash);

        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "different-token-value",
            "eyJhbGciOiJIUzI1NiJ9.otherPayload.signature",
            " "
    })
    @DisplayName("matches должен возвращать false для неправильного токена")
    void matchesShouldReturnFalseForWrongToken(String otherRawToken) {
        when(refreshTokenConfig.getPepper()).thenReturn(PEPPER);

        String storedHash = refreshTokenHasher.hash(RAW_TOKEN);
        boolean result = refreshTokenHasher.matches(otherRawToken, storedHash);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("matches должен возвращать false для мусорного storedHash без исключения")
    void matchesShouldReturnFalseForGarbageHashWithoutException() {
        when(refreshTokenConfig.getPepper()).thenReturn(PEPPER);

        assertThatNoException().isThrownBy(() -> {
            boolean result = refreshTokenHasher.matches(RAW_TOKEN, "гарбаж");
            assertThat(result).isFalse();
        });
    }

    @Test
    @DisplayName("Хеш должен быть строкой из 64 lowercase hex-символов")
    void hashShouldBe64LowercaseHexChars() {
        when(refreshTokenConfig.getPepper()).thenReturn(PEPPER);

        String hash = refreshTokenHasher.hash(RAW_TOKEN);

        assertThat(hash)
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }
}
