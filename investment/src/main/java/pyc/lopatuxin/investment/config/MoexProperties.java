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
@ConfigurationProperties("moex.iss")
public class MoexProperties {

    @NotBlank
    private String baseUrl;

    @Min(1)
    private int timeoutMs = 5000;

    @Min(1)
    private int connectTimeoutMs = 3000;

    @Min(1)
    private int snapshotTtlMinutes = 5;

    @Min(1)
    private int securitiesTtlHours = 1;

    @Min(1)
    private int maxConnections = 50;

    @Min(1)
    private int maxConnectionsPerRoute = 20;
}
