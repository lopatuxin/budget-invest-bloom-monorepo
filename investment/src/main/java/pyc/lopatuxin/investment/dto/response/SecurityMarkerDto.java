package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pyc.lopatuxin.investment.entity.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trade marker for the price chart (plan point 8): the frontend places it on the price
 * series at the nearest point not later than {@link #date}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityMarkerDto {

    private LocalDate date;
    private TransactionType kind;
    private BigDecimal quantity;
    private BigDecimal price;
}
