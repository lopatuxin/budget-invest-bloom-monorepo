package pyc.lopatuxin.investment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceSnapshot {

    @Id
    private String ticker;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal lastPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal previousClose;

    @Column(nullable = false)
    private Instant fetchedAt;
}
