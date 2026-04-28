package pyc.lopatuxin.investment.entity;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PriceHistoryId implements Serializable {

    private String ticker;
    private LocalDate tradeDate;
}
