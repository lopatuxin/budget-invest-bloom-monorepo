package pyc.lopatuxin.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа сервиса API Gateway.
 * <p>
 * Единственная внешняя точка входа для фронтенда, реализованная
 * на базе Spring Cloud Gateway (реактивный WebFlux-стек).
 * Валидирует JWT, обогащает запросы пользовательским контекстом
 * и проксирует их к Auth- и Budget-сервисам.
 * </p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
