package pyc.lopatuxin.investment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import pyc.lopatuxin.investment.config.DividendTaxProperties;
import pyc.lopatuxin.investment.config.MoexProperties;
import pyc.lopatuxin.investment.config.TinvestProperties;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({MoexProperties.class, TinvestProperties.class, DividendTaxProperties.class})
public class InvestmentModuleConfig {
}
