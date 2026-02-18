package pyc.lopatuxin.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pyc.lopatuxin.auth.config.resolver.RequestHeadersResolver;

import java.util.List;

/**
 * Конфигурация Spring MVC.
 * Регистрирует кастомные резолверы аргументов методов контроллеров.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestHeadersResolver requestHeadersResolver;

    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(requestHeadersResolver);
    }
}