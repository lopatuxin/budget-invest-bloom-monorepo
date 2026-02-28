package pyc.lopatuxin.gateway.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import pyc.lopatuxin.gateway.dto.EnrichedRequest;
import pyc.lopatuxin.gateway.dto.UserContext;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Фильтр обогащения запроса пользовательским контекстом из JWT.
 * <p>
 * Извлекает данные аутентифицированного пользователя из {@link JwtAuthenticationToken},
 * строит {@link UserContext} и вшивает его в тело запроса перед проксированием
 * в downstream-сервисы (например, Budget).
 * </p>
 * <p>
 * Для модификации тела используется {@link ModifyRequestBodyGatewayFilterFactory},
 * который корректно буферизует входящий поток и заменяет тело новым содержимым
 * без нарушения реактивной цепочки WebFlux.
 * </p>
 * <p>
 * Применяется только к маршрутам, требующим пользовательского контекста
 * (в частности, {@code budget-all}). Публичные Auth-маршруты фильтр не затрагивает.
 * </p>
 */
@Slf4j
@Component
@NullMarked
@RequiredArgsConstructor
public class UserEnrichmentFilter extends AbstractGatewayFilterFactory<Object> {

    /**
     * Фабрика фильтров для модификации тела входящего запроса.
     * <p>
     * Используется вместо кастомного {@code GlobalFilter} для корректной
     * буферизации и замены тела в реактивном стеке.
     * </p>
     */
    private final ModifyRequestBodyGatewayFilterFactory modifyRequestBodyFilterFactory;

    /**
     * Jackson ObjectMapper для десериализации входящего тела запроса.
     */
    private final ObjectMapper objectMapper;

    /**
     * Создаёт {@link GatewayFilter}, выполняющий обогащение тела запроса.
     * <p>
     * Логика обогащения:
     * <ol>
     *   <li>Тело входящего запроса десериализуется как {@code Map<String, Object>}.</li>
     *   <li>Из {@link ReactiveSecurityContextHolder} извлекается {@link JwtAuthenticationToken}.</li>
     *   <li>Из JWT-claims извлекаются: {@code sub} → {@code userId}, {@code email},
     *       {@code role}, {@code sessionId}.</li>
     *   <li>Строится {@link UserContext} через {@code @Builder}.</li>
     *   <li>Из тела берётся поле {@code data}; при его отсутствии используется всё тело целиком.</li>
     *   <li>Возвращается {@link EnrichedRequest} с заполненными полями {@code user} и {@code data}.</li>
     * </ol>
     * </p>
     *
     * @param config конфигурация фильтра (не используется, передаётся {@code null})
     * @return сконфигурированный {@link GatewayFilter}
     */
    @Override
    public GatewayFilter apply(Object config) {
        ModifyRequestBodyGatewayFilterFactory.Config modifyConfig =
                new ModifyRequestBodyGatewayFilterFactory.Config()
                        .setInClass(String.class)
                        .setOutClass(EnrichedRequest.class)
                        .setRewriteFunction(String.class, EnrichedRequest.class, (_, body) ->
                                ReactiveSecurityContextHolder.getContext()
                                        .flatMap(ctx -> Mono.justOrEmpty(ctx.getAuthentication()))
                                        .cast(JwtAuthenticationToken.class)
                                        .flatMap(jwtToken -> {
                                            Map<String, Object> claims = jwtToken.getToken().getClaims();

                                            String sub = (String) claims.get("sub");
                                            String email = (String) claims.get("email");
                                            String role = (String) claims.get("role");
                                            String sessionIdRaw = (String) claims.get("sessionId");

                                            UUID userId = sub != null ? UUID.fromString(sub) : null;
                                            UUID sessionId = sessionIdRaw != null
                                                    ? UUID.fromString(sessionIdRaw)
                                                    : null;

                                            log.debug("Enriching request for userId {}", userId);

                                            UserContext userContext = UserContext.builder()
                                                    .userId(userId)
                                                    .email(email)
                                                    .role(role)
                                                    .sessionId(sessionId)
                                                    .build();

                                            Object data = resolveData(body);

                                            EnrichedRequest enrichedRequest = EnrichedRequest.builder()
                                                    .user(userContext)
                                                    .data(data)
                                                    .build();

                                            return Mono.just(enrichedRequest);
                                        })
                        );

        return modifyRequestBodyFilterFactory.apply(modifyConfig);
    }

    /**
     * Извлекает полезную нагрузку из тела запроса.
     * <p>
     * Если тело содержит поле {@code data} — возвращает его значение.
     * В противном случае возвращает всё тело целиком как {@code Map<String, Object>}.
     * При пустом или некорректном теле возвращает {@code null}.
     * </p>
     *
     * @param body исходное тело запроса в виде строки JSON
     * @return значение поля {@code data} или всё тело целиком
     */
    private @Nullable Object resolveData(String body) {
        if (body.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> bodyMap = objectMapper.readValue(
                    body,
                    new TypeReference<>() {
                    }
            );
            return bodyMap.getOrDefault("data", bodyMap);
        } catch (Exception _) {
            log.debug("Request body is not a JSON object, using raw body as data");
            return body;
        }
    }
}