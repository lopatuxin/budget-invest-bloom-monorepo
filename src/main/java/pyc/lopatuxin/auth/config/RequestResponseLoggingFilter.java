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
import pyc.lopatuxin.auth.util.SensitiveDataMasker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

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

        // Маскируем чувствительные данные
        String maskedRequestBody = SensitiveDataMasker.maskSensitiveJson(requestBody);
        String maskedResponseBody = SensitiveDataMasker.maskSensitiveJson(responseBody);

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

        // Логируем важные заголовки с маскированием
        appendHeaders(logMessage, request);

        if (!maskedRequestBody.isEmpty()) {
            logMessage.append("  Body: ").append(formatJson(maskedRequestBody)).append("\n");
        }

        logMessage.append("\n");

        // Response logging
        logMessage.append("RESPONSE:\n");
        logMessage.append("  Status: ").append(response.getStatus()).append("\n");
        logMessage.append("  Content-Type: ").append(response.getContentType() != null ? response.getContentType() : "").append("\n");
        logMessage.append("  Duration: ").append(duration).append("ms\n");

        if (!maskedResponseBody.isEmpty()) {
            logMessage.append("  Body: ").append(formatJson(maskedResponseBody)).append("\n");
        }

        logMessage.append("=".repeat(80));

        log.info(logMessage.toString());
    }

    /**
     * Добавляет важные HTTP заголовки в лог с маскированием чувствительных данных
     */
    private void appendHeaders(StringBuilder logMessage, ContentCachingRequestWrapper request) {
        List<String> importantHeaders = List.of("Authorization", "Cookie", "User-Agent", "X-Forwarded-For");

        for (String headerName : importantHeaders) {
            List<String> headerValues = Collections.list(request.getHeaders(headerName));

            if (!headerValues.isEmpty()) {
                String headerValue = String.join(", ", headerValues);
                String maskedValue = SensitiveDataMasker.maskSensitiveHeader(headerName, headerValue);
                logMessage.append("  Header [").append(headerName).append("]: ").append(maskedValue).append("\n");
            }
        }
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