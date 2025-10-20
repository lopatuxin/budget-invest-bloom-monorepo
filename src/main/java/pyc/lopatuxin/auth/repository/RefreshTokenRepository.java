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
     * Поиск токена по хэшу
     *
     * @param tokenHash хэш токена
     * @return токен если найден
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

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
     * Удаление всех токенов пользователя
     *
     * @param user пользователь
     */
    void deleteAllByUser(User user);

    /**
     * Удаление истекших и использованных токенов
     *
     * @param now текущее время
     */
    @Modifying
    @Query("""
            DELETE FROM RefreshToken rt
                WHERE rt.expiresAt < :now OR rt.isUsed = true
            """)
    void deleteExpiredAndUsedTokens(LocalDateTime now);
}