package pyc.lopatuxin.investment.entity;

import jakarta.persistence.*;
import lombok.*;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "dividends")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dividend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_ticker", nullable = false,
            foreignKey = @ForeignKey(name = "fk_dividends_security"))
    private Security security;

    @Column(nullable = false)
    private LocalDate recordDate;

    private LocalDate paymentDate;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal amountPerShare;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DividendStatus status;
}
