package pyc.lopatuxin.investment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioPageRequestDto {

    // Absent or null means the default sort order (WEIGHT); an unrecognized
    // string is rejected by Jackson before this field is ever populated.
    private PortfolioSort sort;
}
