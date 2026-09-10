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

    // Null when MOEX has no last-trade price yet (e.g. a security added in the evening or on a
    // weekend) — see MarketDataService#upsertSnapshot.
    @Column(precision = 15, scale = 2)
    private BigDecimal lastPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal previousClose;

    @Column(nullable = false)
    private Instant fetchedAt;
}
