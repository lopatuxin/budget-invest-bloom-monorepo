package pyc.lopatuxin.investment.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("tinvest")
public class TinvestProperties {

    // Empty is a valid, expected value: an app instance with no TINVEST_TOKEN set still has
    // to start and serve pages from whatever is already in the database (see DividendSyncService).
    private String token = "";

    @NotBlank
    private String baseUrl;

    @Min(1)
    private int timeoutMs = 10000;

    @Min(1)
    private int connectTimeoutMs = 3000;
}
