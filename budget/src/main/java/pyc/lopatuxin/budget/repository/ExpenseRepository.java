package pyc.lopatuxin.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     * Returns total non-transfer expenses for the user over all time.
     * Entries with isTransfer=true (investment operations and asset transfers) are excluded.
     *
     * @param userId identifier of the user
     * @return sum of non-transfer expenses (0 if no records)
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.userId = :userId AND e.isTransfer = false")
    BigDecimal sumNonTransferByUserId(@Param("userId") UUID userId);

    /**
     * Возвращает суммарные не-трансферные расходы пользователя за указанный диапазон дат.
     * Записи с isTransfer=true (инвестиции и переводы между активами) исключаются.
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
              AND e.isTransfer = false
            """)
    Optional<BigDecimal> sumAmountByUserIdAndDateBetween(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Возвращает суммарные не-трансферные расходы по каждой категории для пользователя за указанный диапазон дат.
     * Записи с isTransfer=true (инвестиции и переводы между активами) исключаются.
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
              AND e.isTransfer = false
            GROUP BY e.category.id
            """)
    List<Object[]> sumNonTransferAmountByCategoryForUserAndDateBetween(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Возвращает помесячные суммы расходов пользователя за указанный год.
     *
     * @param userId идентификатор пользователя
     * @param year   календарный год
     * @return список пар [номер месяца (Integer), сумма (BigDecimal)]
     */
    @Query("""
            SELECT MONTH(e.date), SUM(e.amount)
            FROM Expense e
            WHERE e.userId = :userId
              AND YEAR(e.date) = :year
            GROUP BY MONTH(e.date)
            ORDER BY MONTH(e.date)
            """)
    List<Object[]> findMonthlyExpenseByUserIdAndYear(
            @Param("userId") UUID userId,
            @Param("year") int year
    );

    /**
     * Возвращает помесячные суммы не-трансферных расходов пользователя за указанный год.
     * Записи с isTransfer=true (инвестиции и переводы между активами) исключаются.
     *
     * @param userId идентификатор пользователя
     * @param year   календарный год
     * @return список пар [номер месяца (Integer), сумма (BigDecimal)]
     */
    @Query("""
            SELECT MONTH(e.date), SUM(e.amount)
            FROM Expense e
            WHERE e.userId = :userId
              AND YEAR(e.date) = :year
              AND e.isTransfer = false
            GROUP BY MONTH(e.date)
            ORDER BY MONTH(e.date)
            """)
    List<Object[]> findMonthlyNonTransferExpenseByUserIdAndYear(
            @Param("userId") UUID userId,
            @Param("year") int year
    );

    /**
     * Returns monthly non-transfer expense amounts for the user by specific category and year.
     * Entries with isTransfer=true (investment operations and asset transfers) are excluded.
     *
     * @param userId     identifier of the user
     * @param categoryId identifier of the category
     * @param year       calendar year
     * @return list of pairs [month number (Integer), sum (BigDecimal)]
     */
    @Query("""
            SELECT MONTH(e.date), SUM(e.amount)
            FROM Expense e
            WHERE e.userId = :userId
              AND e.category.id = :categoryId
              AND YEAR(e.date) = :year
              AND e.isTransfer = false
            GROUP BY MONTH(e.date)
            ORDER BY MONTH(e.date)
            """)
    List<Object[]> findMonthlyNonTransferExpenseByCategoryAndUserIdAndYear(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("year") int year
    );

    /**
     * Returns yearly non-transfer expense amounts for the user by specific category across all years.
     * Entries with isTransfer=true (investment operations and asset transfers) are excluded.
     *
     * @param userId     identifier of the user
     * @param categoryId identifier of the category
     * @return list of pairs [year (Integer), sum (BigDecimal)]
     */
    @Query("""
            SELECT YEAR(e.date), SUM(e.amount)
            FROM Expense e
            WHERE e.userId = :userId
              AND e.category.id = :categoryId
              AND e.isTransfer = false
            GROUP BY YEAR(e.date)
            ORDER BY YEAR(e.date)
            """)
    List<Object[]> findYearlyNonTransferExpenseByCategoryAndUserId(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId
    );

    /**
     * Возвращает список расходов пользователя по категории за указанный период,
     * отсортированных по дате убывания.
     *
     * @param userId     идентификатор пользователя
     * @param categoryId идентификатор категории
     * @param startDate  первый день периода (включительно)
     * @param endDate    последний день периода (включительно)
     * @return список расходов за период
     */
    @Query("""
            SELECT e FROM Expense e
            JOIN FETCH e.category
            WHERE e.userId = :userId
              AND e.category.id = :categoryId
              AND e.date >= :startDate
              AND e.date <= :endDate
            ORDER BY e.date DESC
            """)
    List<Expense> findByUserIdAndCategoryIdAndDateBetweenOrderByDateDesc(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    long countByCategoryId(UUID categoryId);

    /**
     * Returns aggregated expense stats per category for a given user and year.
     * Each result element: [categoryId (UUID), name (String), emoji (String), monthCount (Long), totalAmount (BigDecimal)].
     *
     * @param userId identifier of the user
     * @param year   calendar year
     * @return list of arrays with category stats
     */
    @Query("""
            SELECT e.category.id, e.category.name, e.category.emoji, COUNT(DISTINCT MONTH(e.date)), SUM(e.amount)
            FROM Expense e
            WHERE e.userId = :userId
              AND YEAR(e.date) = :year
            GROUP BY e.category.id, e.category.name, e.category.emoji
            """)
    List<Object[]> findCategoryStatsByUserIdAndYear(
            @Param("userId") UUID userId,
            @Param("year") int year
    );

    /**
     * Returns aggregated non-transfer expense stats per category for a given user and year.
     * Entries with isTransfer=true (investments and transfers between assets) are excluded.
     * Each result element: [categoryId (UUID), name (String), emoji (String), monthCount (Long), totalAmount (BigDecimal)].
     *
     * @param userId identifier of the user
     * @param year   calendar year
     * @return list of arrays with category stats
     */
    @Query("""
            SELECT e.category.id, e.category.name, e.category.emoji, COUNT(DISTINCT MONTH(e.date)), SUM(e.amount)
            FROM Expense e
            WHERE e.userId = :userId
              AND YEAR(e.date) = :year
              AND e.isTransfer = false
            GROUP BY e.category.id, e.category.name, e.category.emoji
            """)
    List<Object[]> findNonTransferCategoryStatsByUserIdAndYear(
            @Param("userId") UUID userId,
            @Param("year") int year
    );

    /**
     * Массово удаляет все расходы указанной категории.
     *
     * @param categoryId идентификатор категории
     * @return количество удалённых расходов
     */
    @Modifying
    @Query("DELETE FROM Expense e WHERE e.category.id = :categoryId")
    int deleteAllByCategoryId(@Param("categoryId") UUID categoryId);
}