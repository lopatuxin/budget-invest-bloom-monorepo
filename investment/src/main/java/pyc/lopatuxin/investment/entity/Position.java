package pyc.lopatuxin.investment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "positions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_positions_user_ticker",
                columnNames = {"user_id", "security_ticker"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_ticker", nullable = false,
            foreignKey = @ForeignKey(name = "fk_positions_security"))
    private Security security;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal averagePrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @UpdateTimestamp
    private Instant updatedAt;
}
