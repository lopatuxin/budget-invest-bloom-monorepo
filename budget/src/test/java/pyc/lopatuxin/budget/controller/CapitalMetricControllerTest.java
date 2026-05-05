package pyc.lopatuxin.budget.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import pyc.lopatuxin.budget.AbstractIntegrationTest;
import pyc.lopatuxin.budget.entity.CapitalRecord;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Интеграционные тесты CapitalMetricController")
class CapitalMetricControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/budget/metric/capital";

    private UUID userId;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        incomeRepository.deleteAll();
        capitalRecordRepository.deleteAll();
        categoryRepository.deleteAll();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Должен вернуть статус 200 и корректную структуру ответа при наличии данных в БД")
    void shouldReturnOkWithCorrectBodyWhenDataExists() throws Exception {
        capitalRecordRepository.save(CapitalRecord.builder()
                .userId(userId)
                .amount(new BigDecimal("500000.00"))
                .month(1)
                .year(2025)
                .build());

        capitalRecordRepository.save(CapitalRecord.builder()
                .userId(userId)
                .amount(new BigDecimal("550000.00"))
                .month(2)
                .year(2025)
                .build());

        capitalRecordRepository.save(CapitalRecord.builder()
                .userId(userId)
                .amount(new BigDecimal("600000.00"))
                .month(3)
                .year(2025)
                .build());

        String requestBody = buildRequest(userId, 2025);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Метрика капитала успешно получена")))
                .andExpect(jsonPath("$.body.year", is(2025)))
                .andExpect(jsonPath("$.body.currentValue", comparesEqualTo(600000.00)))
                .andExpect(jsonPath("$.body.previousValue", comparesEqualTo(550000.00)))
                .andExpect(jsonPath("$.body.changePercent", notNullValue()))
                .andExpect(jsonPath("$.body.yearlyAverage", notNullValue()))
                .andExpect(jsonPath("$.body.yearlyMax", comparesEqualTo(600000.00)))
                .andExpect(jsonPath("$.body.monthlyData", hasSize(12)))
                .andExpect(jsonPath("$.body.monthlyData[0].month", is(1)))
                .andExpect(jsonPath("$.body.monthlyData[0].monthName", is("Янв")))
                .andExpect(jsonPath("$.body.monthlyData[0].amount", comparesEqualTo(500000.00)))
                .andExpect(jsonPath("$.body.monthlyData[2].amount", comparesEqualTo(600000.00)))
                .andExpect(jsonPath("$.body.monthlyData[3].amount", comparesEqualTo(0)));
    }

    @Test
    @DisplayName("Должен вернуть ответ в структуре ResponseApi (id, status, message, timestamp, body)")
    void shouldReturnResponseMatchingResponseApiContract() throws Exception {
        String requestBody = buildRequest(userId, 2025);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.body", notNullValue()));
    }

    @Test
    @DisplayName("Должен вернуть статус 200 и нулевые показатели при отсутствии данных за указанный год")
    void shouldReturnOkWithZeroValuesWhenNoData() throws Exception {
        String requestBody = buildRequest(userId, 2025);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.year", is(2025)))
                .andExpect(jsonPath("$.body.currentValue", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.previousValue", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.yearlyAverage", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.yearlyMax", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.monthlyData", hasSize(12)));
    }

    @Test
    @DisplayName("Должен вернуть статус 400 при отсутствии параметра year (null)")
    void shouldReturnBadRequestWhenYearIsNull() throws Exception {
        String requestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {}
                }
                """.formatted(userId, UUID.randomUUID());

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @MethodSource("invalidYearValues")
    @DisplayName("Должен вернуть статус 400 при некорректном значении year")
    void shouldReturnBadRequestWhenYearIsOutOfRange(int year) throws Exception {
        String requestBody = buildRequest(userId, year);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    private static Stream<Arguments> invalidYearValues() {
        return Stream.of(
                Arguments.of(1949),
                Arguments.of(2101)
        );
    }

    @Test
    @DisplayName("Должен не учитывать данные капитала другого пользователя")
    void shouldNotIncludeOtherUserData() throws Exception {
        UUID otherUserId = UUID.randomUUID();

        capitalRecordRepository.save(CapitalRecord.builder()
                .userId(otherUserId)
                .amount(new BigDecimal("999999.00"))
                .month(1)
                .year(2025)
                .build());

        capitalRecordRepository.save(CapitalRecord.builder()
                .userId(userId)
                .amount(new BigDecimal("100000.00"))
                .month(1)
                .year(2025)
                .build());

        String requestBody = buildRequest(userId, 2025);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.currentValue", comparesEqualTo(100000.00)))
                .andExpect(jsonPath("$.body.monthlyData[0].amount", comparesEqualTo(100000.00)));
    }

    @Test
    @DisplayName("Должен корректно отображать данные за несколько месяцев с разрывами")
    void shouldHandleNonConsecutiveMonthsCorrectly() throws Exception {
        capitalRecordRepository.save(CapitalRecord.builder()
                .userId(userId)
                .amount(new BigDecimal("200000.00"))
                .month(2)
                .year(2025)
                .build());

        capitalRecordRepository.save(CapitalRecord.builder()
                .userId(userId)
                .amount(new BigDecimal("350000.00"))
                .month(8)
                .year(2025)
                .build());

        capitalRecordRepository.save(CapitalRecord.builder()
                .userId(userId)
                .amount(new BigDecimal("300000.00"))
                .month(12)
                .year(2025)
                .build());

        String requestBody = buildRequest(userId, 2025);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.monthlyData[1].amount", comparesEqualTo(200000.00)))
                .andExpect(jsonPath("$.body.monthlyData[7].amount", comparesEqualTo(350000.00)))
                .andExpect(jsonPath("$.body.monthlyData[11].amount", comparesEqualTo(300000.00)))
                // Месяцы без данных = 0
                .andExpect(jsonPath("$.body.monthlyData[0].amount", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.monthlyData[5].amount", comparesEqualTo(0)))
                .andExpect(jsonPath("$.body.currentValue", comparesEqualTo(300000.00)))
                .andExpect(jsonPath("$.body.previousValue", comparesEqualTo(350000.00)))
                .andExpect(jsonPath("$.body.yearlyMax", comparesEqualTo(350000.00)));
    }

    private String buildRequest(UUID reqUserId, int year) {
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "year": %d
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), year);
    }
}
