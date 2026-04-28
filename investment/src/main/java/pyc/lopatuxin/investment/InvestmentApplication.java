package pyc.lopatuxin.investment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import pyc.lopatuxin.investment.config.MoexProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MoexProperties.class)
public class InvestmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvestmentApplication.class, args);
    }
}
