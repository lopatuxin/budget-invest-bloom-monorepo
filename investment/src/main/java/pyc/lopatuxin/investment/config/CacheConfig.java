package pyc.lopatuxin.investment.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    private final MoexProperties moexProperties;

    public CacheConfig(MoexProperties moexProperties) {
        this.moexProperties = moexProperties;
    }

    @Bean
    CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("moexSnapshots",
                Caffeine.newBuilder()
                        .expireAfterWrite(moexProperties.getSnapshotTtlMinutes(), TimeUnit.MINUTES)
                        .maximumSize(500)
                        .build());
        manager.registerCustomCache("moexSecurities",
                Caffeine.newBuilder()
                        .expireAfterWrite(moexProperties.getSecuritiesTtlHours(), TimeUnit.HOURS)
                        .maximumSize(50)
                        .build());
        return manager;
    }
}
