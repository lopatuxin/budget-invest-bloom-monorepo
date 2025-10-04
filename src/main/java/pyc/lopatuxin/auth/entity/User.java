package pyc.lopatuxin.auth.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    /**
     * Уникальный идентификатор пользователя
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Имя пользователя для входа в систему
     */
    private String username;

    /**
     * Email адрес пользователя
     */
    private String email;

    /**
     * Хэш пароля пользователя
     */
    private String passwordHash;

    /**
     * Имя пользователя
     */
    private String firstName;

    /**
     * Фамилия пользователя
     */
    private String lastName;

    /**
     * Флаг активности аккаунта
     */
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Флаг подтверждения email адреса
     */
    @Builder.Default
    private Boolean isVerified = false;

    /**
     * Время последнего входа в систему
     */
    private LocalDateTime lastLoginAt;

    /**
     * Счетчик неудачных попыток входа
     */
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    /**
     * Время до которого аккаунт заблокирован
     */
    private LocalDateTime lockedUntil;

    /**
     * Время создания записи
     */
    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * Время последнего обновления записи
     */
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Роли пользователя
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserRole> roles;

    /**
     * Refresh токены пользователя
     */
    @OneToMany(mappedBy = "user")
    private List<RefreshToken> refreshTokens;

    /**
     * Токены для сброса пароля
     */
    @OneToMany(mappedBy = "user")
    private List<PasswordResetToken> passwordResetTokens;

    /**
     * Токены для подтверждения email
     */
    @OneToMany(mappedBy = "user")
    private List<EmailVerificationToken> emailVerificationTokens;
}