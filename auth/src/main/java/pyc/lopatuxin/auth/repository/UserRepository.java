package pyc.lopatuxin.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pyc.lopatuxin.auth.entity.User;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Fetches roles eagerly: login() no longer wraps the whole request in a transaction (see
    // LoginService), so by the time JwtService reads user.getRoles() the read-only transaction
    // this query ran in has already closed — a lazy collection would throw
    // LazyInitializationException instead of loading. DISTINCT collapses the duplicate parent
    // rows the join produces for a user with more than one role.
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email")
    Optional<User> findUserByEmail(@Param("email") String email);

    // Same reasoning as findUserByEmail above, keyed by id instead: RefreshTokenService.
    // refreshTokens() looks the user up by the id claim in the refresh token, not by email, and
    // (like login()) is not wrapped in one big @Transactional — a plain findById's lazy roles
    // collection would already be detached by the time JwtService.generateAccessToken reads it.
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") UUID id);

    // A plain existence check: RegisterService only needs to know whether the email is taken, and
    // findUserByEmail's LEFT JOIN FETCH u.roles would pull roles it never reads for that.
    boolean existsByEmail(String email);

    // Atomic UPDATE by id instead of a read-modify-write on a User instance loaded by the caller:
    // two concurrent wrong-password requests must both land their increment, not both compute
    // N+1 from the same in-memory snapshot and overwrite each other (see LoginService).
    // flushAutomatically pairs with clearAutomatically: without it, unflushed changes the caller
    // made to a managed User earlier in the same transaction would be silently lost from the
    // database once this bulk UPDATE runs and the persistence context is cleared.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :id")
    int incrementFailedLoginAttempts(@Param("id") UUID id);

    // Conditional on the current (post-increment) count, re-evaluated by the database at
    // execution time, not on a value read into memory earlier — so two requests that both push
    // the count past the threshold both still set the lock instead of racing on a stale check.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.lockedUntil = :lockedUntil WHERE u.id = :id AND u.failedLoginAttempts >= :threshold")
    int lockIfThresholdReached(@Param("id") UUID id, @Param("lockedUntil") LocalDateTime lockedUntil, @Param("threshold") int threshold);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = null WHERE u.id = :id")
    int resetFailedAttempts(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.lastLoginAt = :lastLoginAt WHERE u.id = :id")
    int updateLastLoginAt(@Param("id") UUID id, @Param("lastLoginAt") LocalDateTime lastLoginAt);
}
