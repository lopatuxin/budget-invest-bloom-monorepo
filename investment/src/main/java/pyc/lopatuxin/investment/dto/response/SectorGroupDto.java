package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SectorGroupDto {

    private String sector;
    private BigDecimal value;
    private BigDecimal percent;
    private int assetsCount;
    private List<PositionResponseDto> positions;
}
