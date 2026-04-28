package pyc.lopatuxin.investment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pyc.lopatuxin.investment.config.MoexProperties;

@SpringBootApplication
@EnableConfigurationProperties(MoexProperties.class)
public class InvestmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvestmentApplication.class, args);
    }
}
