package pyc.lopatuxin.investment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BudgetEntryResponse {
    private UUID entryId;
}
