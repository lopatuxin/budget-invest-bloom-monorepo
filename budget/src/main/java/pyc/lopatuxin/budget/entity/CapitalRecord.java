package pyc.lopatuxin.budget.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Запись о капитале пользователя за конкретный месяц и год.
 * Каждая запись фиксирует общую стоимость активов пользователя
 * на определённый период (месяц + год).
 */
@Entity
@Table(name = "capital_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_capital_user_month_year",
                columnNames = {"user_id", "month", "year"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapitalRecord {

    /**
     * Уникальный идентификатор записи капитала.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Идентификатор пользователя, которому принадлежит запись.
     */
    private UUID userId;

    /**
     * Размер капитала (общая стоимость активов)
     * на указанный месяц и год.
     */
    private BigDecimal amount;

    /**
     * Номер месяца (1-12), за который зафиксирован капитал.
     */
    private Integer month;

    /**
     * Календарный год, за который зафиксирован капитал.
     */
    private Integer year;

    /**
     * Дата и время создания записи.
     */
    @CreationTimestamp
    private Instant createdAt;

    /**
     * Дата и время последнего обновления записи.
     */
    @UpdateTimestamp
    private Instant updatedAt;
}
