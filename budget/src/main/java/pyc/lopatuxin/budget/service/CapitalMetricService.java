package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.response.MetricResponseDto;
import pyc.lopatuxin.budget.repository.CapitalRecordRepository;

import java.util.List;
import java.util.UUID;

/**
 * Сервис для формирования детальной метрики капитала пользователя за год.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CapitalMetricService extends AbstractMetricService {

    private final CapitalRecordRepository capitalRecordRepository;

    /**
     * Формирует детальную метрику капитала за указанный год.
     *
     * @param userId идентификатор пользователя
     * @param year   календарный год
     * @return объект с помесячной разбивкой и агрегированными показателями
     */
    public MetricResponseDto getCapitalMetric(UUID userId, int year) {
        return getMetric(userId, year);
    }

    @Override
    protected List<Object[]> findMonthlyData(UUID userId, int year) {
        return capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year);
    }

    @Override
    protected String getMetricName() {
        return "капитала";
    }
}
