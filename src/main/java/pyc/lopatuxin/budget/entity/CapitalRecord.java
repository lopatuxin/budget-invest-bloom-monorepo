package pyc.lopatuxin.budget.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "capital_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_capital_user_month_year",
                columnNames = {"user_id", "month", "year"}
        ),
        indexes = @Index(name = "idx_capital_user_year", columnList = "user_id, year")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapitalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
