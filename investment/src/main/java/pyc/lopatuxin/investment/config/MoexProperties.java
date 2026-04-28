package pyc.lopatuxin.investment.config;

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

    private int timeoutMs = 5000;

    private int snapshotTtlMinutes = 5;
}
