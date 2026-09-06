package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.repository.CapitalRecordRepository;

import java.util.List;
import java.util.UUID;

/**
 * Сервис для формирования детальной метрики капитала пользователя за год.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(value = "budgetTransactionManager", readOnly = true)
public class CapitalMetricService extends AbstractMetricService {

    private final CapitalRecordRepository capitalRecordRepository;

    @Override
    protected List<Object[]> findMonthlyData(UUID userId, int year) {
        return capitalRecordRepository.findMonthlyCapitalByUserIdAndYear(userId, year);
    }

    @Override
    protected String getMetricName() {
        return "капитала";
    }
}
