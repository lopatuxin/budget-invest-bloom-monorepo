package pyc.lopatuxin.investment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

/**
 * Security dictionary block of the security page (plan point 9): name, ticker and the chips
 * shown under them. {@code sector} is absent for a security without one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityPageSecurityDto {

    private String ticker;
    private String name;
    private SecurityType securityType;
    private String sector;
    private String boardId;
    private HistoryStatus historyStatus;
}
