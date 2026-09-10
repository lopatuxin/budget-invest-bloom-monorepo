package pyc.lopatuxin.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pyc.lopatuxin.auth.entity.RefreshToken;
import pyc.lopatuxin.auth.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Поиск всех активных токенов пользователя
     *
     * @param user пользователь
     * @return список активных токенов
     */
    @Query("""
            FROM RefreshToken rt
                WHERE rt.user = :user
                AND rt.isUsed = false
                AND rt.expiresAt > :now
            """)
    List<RefreshToken> findActiveTokensByUser(User user, LocalDateTime now);

    /**
     * Finds a single active refresh token by user and exact token hash.
     * Uses index on (user_id, token_hash) — O(1) instead of full scan.
     *
     * @param user      token owner
     * @param tokenHash HMAC-SHA256 hex hash of the raw token
     * @param now       current timestamp for expiry check
     * @return matching active token if present
     */
    @Query("""
            FROM RefreshToken rt
                WHERE rt.user = :user
                AND rt.tokenHash = :tokenHash
                AND rt.isUsed = false
                AND rt.expiresAt > :now
            """)
    Optional<RefreshToken> findActiveByUserAndTokenHash(User user, String tokenHash, LocalDateTime now);

    /**
     * Atomically marks a token as used, but only if it is still active (not expired, not already
     * used) — the check and the write happen as one statement instead of a read-then-write, so two
     * concurrent presentations of the same token can never both see "not used yet" and both
     * proceed. Exactly one caller can ever get {@code 1} back for a given row; every other
     * concurrent (or later) caller gets {@code 0}, which {@code RefreshTokenService} treats as
     * reuse.
     *
     * @param user      token owner
     * @param tokenHash HMAC-SHA256 hex hash of the raw token
     * @param now       current timestamp for expiry check
     * @return number of rows updated: 1 on success, 0 if the token was already used, expired, or never existed
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken rt
                SET rt.isUsed = true
                WHERE rt.user = :user
                AND rt.tokenHash = :tokenHash
                AND rt.expiresAt > :now
                AND rt.isUsed = false
            """)
    int markUsedIfActive(User user, String tokenHash, LocalDateTime now);

    /**
     * Tells apart "never existed / expired" (matches nothing here) from a genuine replay of an
     * already-rotated token (matches, {@code isUsed = true}) — used only after
     * {@link #markUsedIfActive} returns {@code 0}, to decide whether that was reuse or simply an
     * unknown/expired token.
     *
     * @param user      token owner
     * @param tokenHash HMAC-SHA256 hex hash of the raw token
     * @param now       current timestamp for expiry check
     * @return true if an active-window row for this hash exists and is already used
     */
    boolean existsByUserAndTokenHashAndExpiresAtAfterAndIsUsedTrue(User user, String tokenHash, LocalDateTime now);

    /**
     * Удаление всех токенов пользователя
     *
     * @param user пользователь
     */
    void deleteAllByUser(User user);

    // No expired/used-token cleanup job exists yet — if one is ever added, it must delete only
    // rt.isUsed = false AND rt.expiresAt < :now (expired but never used). Deleting isUsed = true
    // rows would erase the very evidence existsByUserAndTokenHashAndExpiresAtAfterAndIsUsedTrue
    // relies on to tell a genuine replay apart from an unknown/expired token, silently disabling
    // reuse detection for any token old enough to be swept.
}