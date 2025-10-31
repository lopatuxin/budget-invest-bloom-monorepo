package pyc.lopatuxin.auth.config.resolver;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import pyc.lopatuxin.auth.dto.request.RequestHeadersDto;

/**
 * Резолвер для автоматического извлечения HTTP заголовков в {@link RequestHeadersDto}.
 * <p>
 * Этот резолвер активируется для параметров методов контроллера типа {@link RequestHeadersDto}
 * и автоматически заполняет объект данными из HTTP заголовков запроса.
 * Использует {@link BearerTokenResolver} для извлечения JWT токена из заголовка Authorization.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class RequestHeadersResolver implements HandlerMethodArgumentResolver {

    private final BearerTokenResolver bearerTokenResolver;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(RequestHeadersDto.class);
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return RequestHeadersDto.builder().build();
        }

        String jwt = bearerTokenResolver.resolve(request);

        return RequestHeadersDto.builder()
                .authorization(request.getHeader("Authorization"))
                .jwt(jwt)
                .refreshToken(request.getHeader("X-Refresh-Token"))
                .userAgent(request.getHeader("User-Agent"))
                .xForwardedFor(request.getHeader("X-Forwarded-For"))
                .build();
    }
}