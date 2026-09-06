package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Override
    protected List<Object[]> findMonthlyData(UUID userId, int year) {
        return incomeRepository.findMonthlyNonTransferIncomeByUserIdAndYear(userId, year);
    }

    @Override
    protected String getMetricName() {
        return "доходов";
    }
}
