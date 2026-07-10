package pyc.lopatuxin.security.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import pyc.lopatuxin.shared.dto.ApiRequest;
import pyc.lopatuxin.shared.dto.UserContextDto;
import pyc.lopatuxin.shared.enums.UserRole;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;

/**
 * Fills {@link ApiRequest#getUser()} from the authenticated JWT for every controller
 * that accepts an {@link ApiRequest} body — the servlet-stack equivalent of the former
 * gateway's UserEnrichmentFilter. Any client-supplied {@code user} block is overwritten.
 */
@Slf4j
@ControllerAdvice
public class AuthenticatedUserRequestBodyAdvice implements RequestBodyAdvice {

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (targetType instanceof ParameterizedType parameterizedType) {
            return parameterizedType.getRawType() == ApiRequest.class;
        }
        return targetType == ApiRequest.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return inputMessage;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof ApiRequest<?> req) {
            // Fail-closed: wipe any client-supplied user block first, then repopulate it
            // from the JWT only. If no valid JWT context is available, user stays null
            // and downstream @NotNull validation rejects the request instead of trusting the client.
            req.setUser(null);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                UserContextDto userContext = buildUserContext(jwtAuth);
                if (userContext != null) {
                    req.setUser(userContext);
                }
            }
        }
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    private UserContextDto buildUserContext(JwtAuthenticationToken jwtAuth) {
        Map<String, Object> claims = jwtAuth.getToken().getClaims();
        String userIdRaw = (String) claims.get("userId");
        if (userIdRaw == null) {
            return null;
        }

        return UserContextDto.builder()
                .userId(UUID.fromString(userIdRaw))
                .email((String) claims.get("sub"))
                .role(parseRole((String) claims.get("role")))
                .sessionId(parseUuid((String) claims.get("sessionId")))
                .build();
    }

    private UserRole parseRole(String role) {
        if (role == null) {
            return UserRole.USER;
        }
        try {
            return UserRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown role claim '{}' in JWT, defaulting to USER", role);
            return UserRole.USER;
        }
    }

    private UUID parseUuid(String value) {
        return value != null ? UUID.fromString(value) : null;
    }
}
