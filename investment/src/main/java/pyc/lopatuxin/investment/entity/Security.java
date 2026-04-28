package pyc.lopatuxin.investment.entity;

import jakarta.persistence.*;
import lombok.*;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.time.Instant;

@Entity
@Table(name = "securities")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Security {

    @Id
    private String ticker;

    @Column(name = "board_id", length = 20)
    private String boardId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecurityType type;

    private String sector;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HistoryStatus historyStatus;

    private Instant lastPriceUpdatedAt;
}
