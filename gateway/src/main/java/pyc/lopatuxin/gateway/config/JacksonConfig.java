package pyc.lopatuxin.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Jackson для API Gateway.
 * <p>
 * Регистрирует {@link ObjectMapper} как бин Spring-контекста с настройками,
 * необходимыми для корректной сериализации/десериализации запросов и ответов.
 * </p>
 * <p>
 * Подключает {@link JavaTimeModule} для поддержки {@code java.time.*} типов
 * и отключает запись дат как числовых timestamp-значений.
 * </p>
 */
@Configuration
public class JacksonConfig {

    /**
     * Создаёт и регистрирует {@link ObjectMapper} с настройками проекта.
     * <p>
     * Используется {@link pyc.lopatuxin.gateway.filter.UserEnrichmentFilter}
     * для десериализации тела входящего запроса.
     * </p>
     *
     * @return настроенный {@link ObjectMapper}
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}