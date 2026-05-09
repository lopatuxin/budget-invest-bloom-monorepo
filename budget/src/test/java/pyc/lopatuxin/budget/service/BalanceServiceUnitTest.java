package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.response.LifetimeBalanceResponseDto;
import pyc.lopatuxin.budget.repository.ExpenseRepository;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BalanceServiceUnitTest")
class BalanceServiceUnitTest {

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private BalanceService balanceService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getLifetimeBalance: freeCapital = totalIncome - totalExpense")
    void getLifetimeBalance_shouldReturnCorrectFreeCapital() {
        when(incomeRepository.sumByUserId(userId)).thenReturn(new BigDecimal("500000.00"));
        when(expenseRepository.sumByUserId(userId)).thenReturn(new BigDecimal("375000.00"));

        LifetimeBalanceResponseDto result = balanceService.getLifetimeBalance(userId);

        assertThat(result.getTotalIncome()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(result.getTotalExpense()).isEqualByComparingTo(new BigDecimal("375000.00"));
        assertThat(result.getFreeCapital()).isEqualByComparingTo(new BigDecimal("125000.00"));
    }

    @Test
    @DisplayName("getLifetimeBalance: при нулевых доходах и расходах — возвращает нули, без NPE")
    void getLifetimeBalance_shouldReturnZeroesWhenNoData() {
        // COALESCE(SUM(...), 0) в репозитории возвращает BigDecimal.ZERO при пустой таблице
        when(incomeRepository.sumByUserId(userId)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumByUserId(userId)).thenReturn(BigDecimal.ZERO);

        LifetimeBalanceResponseDto result = balanceService.getLifetimeBalance(userId);

        assertThat(result).isNotNull();
        assertThat(result.getTotalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getFreeCapital()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getLifetimeBalance: фильтрует по userId — суммы другого пользователя не учитываются")
    void getLifetimeBalance_shouldFilterByUserId() {
        // Service delegates filtering to the repository; we verify that it calls sumByUserId(userId)
        // and returns exactly what that call returns — not any data for another user.
        when(incomeRepository.sumByUserId(userId)).thenReturn(new BigDecimal("100000.00"));
        when(expenseRepository.sumByUserId(userId)).thenReturn(new BigDecimal("40000.00"));

        LifetimeBalanceResponseDto result = balanceService.getLifetimeBalance(userId);

        assertThat(result.getTotalIncome()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(result.getTotalExpense()).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(result.getFreeCapital()).isEqualByComparingTo(new BigDecimal("60000.00"));
    }

    @Test
    @DisplayName("getLifetimeBalance: когда расходы превышают доходы — freeCapital отрицательный")
    void getLifetimeBalance_shouldReturnNegativeFreeCapitalWhenExpensesExceedIncome() {
        when(incomeRepository.sumByUserId(userId)).thenReturn(new BigDecimal("10000.00"));
        when(expenseRepository.sumByUserId(userId)).thenReturn(new BigDecimal("15000.00"));

        LifetimeBalanceResponseDto result = balanceService.getLifetimeBalance(userId);

        assertThat(result.getFreeCapital()).isEqualByComparingTo(new BigDecimal("-5000.00"));
    }
}
