package pyc.lopatuxin.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (shouldNotLog(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        String timestamp = LocalDateTime.now().format(FORMATTER);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logRequestAndResponse(wrappedRequest, wrappedResponse, timestamp, duration);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private boolean shouldNotLog(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.contains("/actuator") ||
               uri.contains("/favicon.ico") ||
               uri.contains("/swagger") ||
               uri.contains("/api-docs");
    }

    private void logRequestAndResponse(ContentCachingRequestWrapper request,
                                     ContentCachingResponseWrapper response,
                                     String timestamp,
                                     long duration) {

        String requestBody = getRequestBody(request);
        String responseBody = getResponseBody(response);

        StringBuilder logMessage = new StringBuilder("\n");
        logMessage.append("=".repeat(80)).append("\n");
        logMessage.append("HTTP REQUEST/RESPONSE LOG [").append(timestamp).append("]\n");
        logMessage.append("=".repeat(80)).append("\n");

        // Request logging
        logMessage.append("REQUEST:\n");
        logMessage.append("  Method: ").append(request.getMethod()).append("\n");
        logMessage.append("  URI: ").append(request.getRequestURI()).append("\n");
        logMessage.append("  Query: ").append(request.getQueryString() != null ? request.getQueryString() : "").append("\n");
        logMessage.append("  Content-Type: ").append(request.getContentType() != null ? request.getContentType() : "").append("\n");

        if (!requestBody.isEmpty()) {
            logMessage.append("  Body: ").append(formatJson(requestBody)).append("\n");
        }

        logMessage.append("\n");

        // Response logging
        logMessage.append("RESPONSE:\n");
        logMessage.append("  Status: ").append(response.getStatus()).append("\n");
        logMessage.append("  Content-Type: ").append(response.getContentType() != null ? response.getContentType() : "").append("\n");
        logMessage.append("  Duration: ").append(duration).append("ms\n");

        if (!responseBody.isEmpty()) {
            logMessage.append("  Body: ").append(formatJson(responseBody)).append("\n");
        }

        logMessage.append("=".repeat(80));

        log.info(logMessage.toString());
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, StandardCharsets.UTF_8);
        }
        return "";
    }

    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, StandardCharsets.UTF_8);
        }
        return "";
    }

    private String formatJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "";
        }

        try {
            // Простое форматирование JSON для читаемости
            return json.trim().replace(",", ",\n    ").replace("{", "{\n    ").replace("}", "\n  }");
        } catch (Exception _) {
            return json;
        }
    }
}