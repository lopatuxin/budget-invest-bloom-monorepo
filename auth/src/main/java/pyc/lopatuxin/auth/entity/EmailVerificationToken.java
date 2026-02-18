package pyc.lopatuxin.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EmailVerificationToken {

    /**
     * Уникальный идентификатор токена
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Пользователь
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    /**
     * Хэш токена верификации
     */
    private String tokenHash;

    /**
     * Время истечения токена
     */
    private LocalDateTime expiresAt;

    /**
     * Флаг использования токена
     */
    @Builder.Default
    private Boolean isUsed = false;

    /**
     * Время создания
     */
    private LocalDateTime createdAt;
}