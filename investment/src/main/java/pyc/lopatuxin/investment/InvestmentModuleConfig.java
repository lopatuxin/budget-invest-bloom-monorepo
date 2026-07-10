package pyc.lopatuxin.investment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import pyc.lopatuxin.investment.config.MoexProperties;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MoexProperties.class)
public class InvestmentModuleConfig {
}
