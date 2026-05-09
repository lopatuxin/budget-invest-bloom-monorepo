package pyc.lopatuxin.investment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BudgetApiResponse<T> {
    private String code;
    private String message;
    private T body;
}
