package pyc.lopatuxin.budget.service;

import org.springframework.stereotype.Service;
import pyc.lopatuxin.budget.dto.response.CategorySummaryDto;
import pyc.lopatuxin.budget.entity.Category;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Builder service for constructing category summary DTOs.
 * Contains pure calculation logic without repository dependencies.
 */
@Service
public class CategorySummaryBuilder {

    /**
     * Builds a summary DTO for a single category.
     *
     * @param category           the category entity
     * @param expensesByCategory map of categoryId to total expenses amount
     * @return category summary DTO
     */
    public CategorySummaryDto buildCategorySummary(Category category, Map<UUID, BigDecimal> expensesByCategory) {
        BigDecimal amount = expensesByCategory.getOrDefault(category.getId(), BigDecimal.ZERO);
        BigDecimal budget = category.getBudget();
        BigDecimal percentUsed = calculatePercentUsed(amount, budget);

        return CategorySummaryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .emoji(category.getEmoji())
                .amount(amount)
                .budget(budget)
                .percentUsed(percentUsed)
                .build();
    }

    /**
     * Calculates the percentage of budget used, capped at 100%.
     * Returns 0 if budget is zero.
     *
     * @param amount actual expenses
     * @param budget budget limit
     * @return percentage used (0-100)
     */
    public BigDecimal calculatePercentUsed(BigDecimal amount, BigDecimal budget) {
        if (budget.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal percent = calculatePercent(amount, budget);

        return percent.min(new BigDecimal("100.00"));
    }

    private BigDecimal calculatePercent(BigDecimal amount, BigDecimal budget) {
        return amount.divide(budget, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
