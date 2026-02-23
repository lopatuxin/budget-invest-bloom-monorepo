package pyc.lopatuxin.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pyc.lopatuxin.budget.entity.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с расходами пользователя.
 */
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    /**
     * Возвращает суммарные расходы пользователя за указанный диапазон дат.
     *
     * @param userId    идентификатор пользователя
     * @param startDate первый день периода (включительно)
     * @param endDate   последний день периода (включительно)
     * @return Optional с суммой расходов, или пустой если записей нет
     */
    @Query("""
            SELECT SUM(e.amount)
            FROM Expense e
            WHERE e.userId = :userId
              AND e.date >= :startDate
              AND e.date <= :endDate
            """)
    Optional<BigDecimal> sumAmountByUserIdAndDateBetween(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Возвращает суммарные расходы по каждой категории для пользователя за указанный диапазон дат.
     * Каждый элемент результата — массив из двух значений: [categoryId (UUID), sum (BigDecimal)].
     *
     * @param userId    идентификатор пользователя
     * @param startDate первый день периода (включительно)
     * @param endDate   последний день периода (включительно)
     * @return список массивов [categoryId, totalAmount] сгруппированных по категории
     */
    @Query("""
            SELECT e.category.id, SUM(e.amount)
            FROM Expense e
            WHERE e.userId = :userId
              AND e.date >= :startDate
              AND e.date <= :endDate
            GROUP BY e.category.id
            """)
    List<Object[]> sumAmountByCategoryForUserAndDateBetween(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Возвращает суммарные расходы пользователя за указанный год.
     * Используется для расчёта личной инфляции.
     *
     * @param userId идентификатор пользователя
     * @param year   год, за который суммируются расходы
     * @return Optional с суммой расходов за год, или пустой если записей нет
     */
    @Query("""
            SELECT SUM(e.amount)
            FROM Expense e
            WHERE e.userId = :userId
              AND YEAR(e.date) = :year
            """)
    Optional<BigDecimal> sumAmountByUserIdAndYear(
            @Param("userId") UUID userId,
            @Param("year") int year
    );
}