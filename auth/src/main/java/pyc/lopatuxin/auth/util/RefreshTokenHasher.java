package pyc.lopatuxin.auth.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pyc.lopatuxin.auth.config.RefreshTokenConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes refresh tokens with HMAC-SHA256 using a server-side pepper.
 * Deterministic — allows index lookup by token_hash instead of a full table scan.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final RefreshTokenConfig refreshTokenConfig;

    /**
     * Computes HMAC-SHA256(rawToken, pepper) and returns a lowercase hex string (64 chars).
     */
    public String hash(String rawToken) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    refreshTokenConfig.getPepper().getBytes(StandardCharsets.UTF_8),
                    ALGORITHM
            );
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 for refresh token", e);
        }
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    public boolean matches(String rawToken, String storedHash) {
        String computed = hash(rawToken);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

}
