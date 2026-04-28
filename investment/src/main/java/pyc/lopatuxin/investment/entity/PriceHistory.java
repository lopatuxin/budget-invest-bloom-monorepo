package pyc.lopatuxin.investment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "price_history")
@IdClass(PriceHistoryId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceHistory {

    @Id
    private String ticker;

    @Id
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal open;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal close;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal high;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal low;

    private Long volume;
}
