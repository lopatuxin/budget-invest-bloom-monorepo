package pyc.lopatuxin.auth.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилита для маскирования чувствительных данных в логах.
 * Обеспечивает защиту паролей, токенов и другой конфиденциальной информации.
 */
@Slf4j
@UtilityClass
public class SensitiveDataMasker {

    /**
     * Маска для замены чувствительных данных
     */
    private static final String MASK = "***MASKED***";

    /**
     * Набор чувствительных полей в JSON, которые нужно маскировать
     */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password",
            "accessToken",
            "refreshToken",
            "token"
    );

    /**
     * Набор чувствительных HTTP заголовков
     */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "cookie"
    );

    /**
     * Паттерн для поиска JSON полей: "fieldName":"value" или "fieldName": "value"
     * Поддерживает значения с пробелами, специальными символами и unicode
     */
    private static final Pattern JSON_FIELD_PATTERN = Pattern.compile(
            "\"(\\w+)\"\\s*:\\s*\"([^\"]*)\""
    );

    /**
     * Маскирует чувствительные данные в JSON строке
     *
     * @param json JSON строка для маскирования
     * @return JSON строка с замаскированными чувствительными полями
     */
    public static String maskSensitiveJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }

        try {
            String maskedJson = json;
            Matcher matcher = JSON_FIELD_PATTERN.matcher(json);

            while (matcher.find()) {
                String fieldName = matcher.group(1);
                String fieldValue = matcher.group(2);

                if (SENSITIVE_FIELDS.contains(fieldName) && !fieldValue.isBlank()) {
                    String originalPattern = "\"" + fieldName + "\"\\s*:\\s*\"" + Pattern.quote(fieldValue) + "\"";
                    String replacement = "\"" + fieldName + "\": \"" + MASK + "\"";
                    maskedJson = maskedJson.replaceAll(originalPattern, replacement);
                }
            }

            return maskedJson;
        } catch (Exception e) {
            log.warn("Ошибка при маскировании JSON, возвращаю исходную строку", e);
            return json;
        }
    }

    /**
     * Маскирует чувствительные HTTP заголовки
     *
     * @param headerName  название заголовка
     * @param headerValue значение заголовка
     * @return замаскированное значение, если заголовок чувствительный, иначе исходное значение
     */
    public static String maskSensitiveHeader(String headerName, String headerValue) {
        if (headerName == null || headerValue == null) {
            return headerValue;
        }

        String normalizedHeaderName = headerName.toLowerCase().trim();

        if (SENSITIVE_HEADERS.contains(normalizedHeaderName)) {
            return maskTokenValue(headerValue);
        }

        return headerValue;
    }

    /**
     * Маскирует токен, оставляя видимыми только первые и последние несколько символов
     * для целей отладки
     *
     * @param value значение токена
     * @return частично замаскированное значение
     */
    private static String maskTokenValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        if (value.toLowerCase().startsWith("bearer ")) {
            String token = value.substring(7);
            return "Bearer " + partiallyMaskToken(token);
        }

        if (value.contains("=")) {
            return maskCookieValue(value);
        }

        return partiallyMaskToken(value);
    }

    /**
     * Частично маскирует токен, показывая первые 4 и последние 4 символа
     *
     * @param token токен для маскирования
     * @return частично замаскированный токен
     */
    private static String partiallyMaskToken(String token) {
        if (token == null || token.length() <= 8) {
            return MASK;
        }

        String prefix = token.substring(0, 4);
        String suffix = token.substring(token.length() - 4);
        return prefix + "..." + MASK + "..." + suffix;
    }

    /**
     * Маскирует значения в Cookie заголовке
     *
     * @param cookieHeader значение Cookie заголовка
     * @return Cookie заголовок с замаскированными чувствительными значениями
     */
    private static String maskCookieValue(String cookieHeader) {
        String[] cookies = cookieHeader.split(";");
        StringBuilder maskedCookies = new StringBuilder();

        for (String cookie : cookies) {
            String trimmedCookie = cookie.trim();
            String[] parts = trimmedCookie.split("=", 2);

            if (parts.length == 2) {
                String cookieName = parts[0].trim();
                String cookieValue = parts[1].trim();

                if (cookieName.toLowerCase().contains("token") ||
                        cookieName.toLowerCase().contains("auth") ||
                        cookieName.toLowerCase().contains("session")) {
                    maskedCookies.append(cookieName).append("=").append(partiallyMaskToken(cookieValue));
                } else {
                    maskedCookies.append(trimmedCookie);
                }
            } else {
                maskedCookies.append(trimmedCookie);
            }

            maskedCookies.append("; ");
        }

        if (!maskedCookies.isEmpty()) {
            maskedCookies.setLength(maskedCookies.length() - 2);
        }

        return maskedCookies.toString();
    }
}