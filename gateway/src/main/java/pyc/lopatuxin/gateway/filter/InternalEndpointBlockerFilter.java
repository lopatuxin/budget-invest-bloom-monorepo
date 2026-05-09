package pyc.lopatuxin.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Blocks any external access to internal API endpoints.
 * These endpoints (e.g. /api/budget/internal/**) are intended for service-to-service calls
 * inside the docker network only and must never be reachable through the gateway.
 *
 * Runs at HIGHEST_PRECEDENCE — before any Spring Cloud Gateway routing.
 */
@Component
public class InternalEndpointBlockerFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/api/budget/internal/") || path.startsWith("/api/investment/internal/")) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
