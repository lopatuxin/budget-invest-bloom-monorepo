package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.response.LifetimeBalanceResponseDto;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service for computing lifetime balance aggregates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalanceService {

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;

    /**
     * Computes the lifetime balance for the given user.
     *
     * @param userId identifier of the user
     * @return DTO with totalIncome, totalExpense and freeCapital
     */
    public LifetimeBalanceResponseDto getLifetimeBalance(UUID userId) {
        log.debug("Computing lifetime balance for userId={}", userId);

        BigDecimal totalIncome = incomeRepository.sumByUserId(userId);
        BigDecimal totalExpense = expenseRepository.sumByUserId(userId);
        BigDecimal freeCapital = totalIncome.subtract(totalExpense);

        return LifetimeBalanceResponseDto.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .freeCapital(freeCapital)
                .build();
    }
}
