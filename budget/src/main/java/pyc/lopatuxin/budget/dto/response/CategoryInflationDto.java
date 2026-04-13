package pyc.lopatuxin.budget.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inflation breakdown for a single expense category.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryInflationDto {

    private UUID categoryId;
    private String categoryName;
    private String emoji;
    private BigDecimal avgCurrent;
    private BigDecimal avgPrevious;
    private BigDecimal changePercent;
    private BigDecimal contribution;
    private BigDecimal weightPercent;
}
