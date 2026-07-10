package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.response.MetricResponseDto;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.util.List;
import java.util.UUID;

/**
 * Сервис для формирования детальной метрики доходов пользователя за год.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(value = "budgetTransactionManager", readOnly = true)
public class IncomeMetricService extends AbstractMetricService {

    private final IncomeRepository incomeRepository;

    /**
     * Формирует детальную метрику доходов за указанный год.
     *
     * @param userId идентификатор пользователя
     * @param year   календарный год
     * @return объект с помесячной разбивкой и агрегированными показателями
     */
    public MetricResponseDto getIncomeMetric(UUID userId, int year) {
        return getMetric(userId, year);
    }

    @Override
    protected List<Object[]> findMonthlyData(UUID userId, int year) {
        return incomeRepository.findMonthlyNonTransferIncomeByUserIdAndYear(userId, year);
    }

    @Override
    protected String getMetricName() {
        return "доходов";
    }
}
