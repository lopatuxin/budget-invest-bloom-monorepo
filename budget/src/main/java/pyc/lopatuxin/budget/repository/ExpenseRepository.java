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
     * Возвращает суммарные не-трансферные расходы пользователя с датой не позже указанной —
     * накопительный итог на начало точки истории капитала. Записи с isTransfer=true исключаются.
     *
     * @param userId идентификатор пользователя
     * @param date   дата, до которой (включительно) считается сумма
     * @return сумма расходов (0 если записей нет)
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.userId = :userId AND e.date <= :date AND e.isTransfer = false")
    BigDecimal sumNonTransferByUserIdAndDateLessThanEqual(@Param("userId") UUID userId, @Param("date") LocalDate date);

    /**
     * Возвращает помесячные суммы не-трансферных расходов пользователя за произвольный диапазон дат
     * (может охватывать несколько лет). Записи с isTransfer=true исключаются.
     *
     * @param userId    идентификатор пользователя
     * @param startDate первый день диапазона (включительно)
     * @param endDate   последний день диапазона (включительно)
     * @return список массивов [year (Integer), month (Integer), сумма (BigDecimal)]
     */
    @Query("""
            SELECT YEAR(e.date), MONTH(e.date), SUM(e.amount)
            FROM Expense e
            WHERE e.userId = :userId
              AND e.date >= :startDate
              AND e.date <= :endDate
              AND e.isTransfer = false
            GROUP BY YEAR(e.date), MONTH(e.date)
            """)
    List<Object[]> findMonthlyNonTransferExpenseByUserIdAndDateBetween(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

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
              AND e.isTransfer = false
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
     * Returns aggregated non-transfer expense totals per category for a given user and year.
     * Entries with isTransfer=true (investments and transfers between assets) are excluded.
     * Each result element: [categoryId (UUID), name (String), emoji (String), totalAmount (BigDecimal)].
     *
     * @param userId identifier of the user
     * @param year   calendar year
     * @return list of arrays with category stats
     */
    @Query("""
            SELECT e.category.id, e.category.name, e.category.emoji, SUM(e.amount)
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

    /**
     * Возвращает не-трансферные расходы пользователя за месяц вместе с категорией (без N+1),
     * используется лентой операций. Записи с isTransfer=true исключаются.
     *
     * @param userId    идентификатор пользователя
     * @param startDate первый день месяца (включительно)
     * @param endDate   последний день месяца (включительно)
     * @return список расходов месяца с загруженной категорией
     */
    @Query("""
            SELECT e FROM Expense e
            JOIN FETCH e.category
            WHERE e.userId = :userId
              AND e.date >= :startDate
              AND e.date <= :endDate
              AND e.isTransfer = false
            """)
    List<Expense> findByUserIdAndDateBetweenAndIsTransferFalse(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Возвращает число не-трансферных расходов пользователя по каждой категории начиная с указанной даты.
     * Используется для сортировки категорий по частоте использования в форме операции.
     *
     * @param userId    идентификатор пользователя
     * @param sinceDate дата начала периода (включительно)
     * @return список массивов [categoryId (UUID), count (Long)]
     */
    @Query("""
            SELECT e.category.id, COUNT(e)
            FROM Expense e
            WHERE e.userId = :userId
              AND e.date >= :sinceDate
              AND e.isTransfer = false
            GROUP BY e.category.id
            """)
    List<Object[]> countNonTransferExpensesByCategorySince(
            @Param("userId") UUID userId,
            @Param("sinceDate") LocalDate sinceDate
    );

    /**
     * Возвращает помесячные агрегаты не-трансферных расходов за окно истории: для каждого месяца окна,
     * в котором есть хотя бы одна запись, — сумму записей с датой до дня {@code day} включительно,
     * сумму за полный месяц и число различных дней месяца, на которые приходятся записи (используется
     * для определения границы, с которой у пользователя начался подневный учёт). Месяцы без записей
     * в результат не попадают. Используется для расчёта нормы («обычно к этому дню») по расходам в целом.
     *
     * @param userId    идентификатор пользователя
     * @param startDate первый день окна (включительно)
     * @param endDate   последний день окна (включительно)
     * @param day       день месяца, до которого считается частичная сумма
     * @return список массивов [year (Integer), month (Integer), cutoffSum (BigDecimal), fullSum (BigDecimal), distinctDays (Long)]
     */
    @Query("""
            SELECT YEAR(e.date), MONTH(e.date),
                   SUM(CASE WHEN DAY(e.date) <= :day THEN e.amount ELSE 0 END),
                   SUM(e.amount),
                   COUNT(DISTINCT DAY(e.date))
            FROM Expense e
            WHERE e.userId = :userId
              AND e.date >= :startDate
              AND e.date <= :endDate
              AND e.isTransfer = false
            GROUP BY YEAR(e.date), MONTH(e.date)
            """)
    List<Object[]> findWindowedNonTransferExpenseStats(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("day") int day
    );

    /**
     * То же, что {@link #findWindowedNonTransferExpenseStats}, но с разбивкой по категориям.
     * Используется для расчёта нормы по каждой категории.
     *
     * @param userId    идентификатор пользователя
     * @param startDate первый день окна (включительно)
     * @param endDate   последний день окна (включительно)
     * @param day       день месяца, до которого считается частичная сумма
     * @return список массивов [year (Integer), month (Integer), categoryId (UUID), cutoffSum (BigDecimal), fullSum (BigDecimal)]
     */
    @Query("""
            SELECT YEAR(e.date), MONTH(e.date), e.category.id,
                   SUM(CASE WHEN DAY(e.date) <= :day THEN e.amount ELSE 0 END),
                   SUM(e.amount)
            FROM Expense e
            WHERE e.userId = :userId
              AND e.date >= :startDate
              AND e.date <= :endDate
              AND e.isTransfer = false
            GROUP BY YEAR(e.date), MONTH(e.date), e.category.id
            """)
    List<Object[]> findWindowedNonTransferExpenseStatsByCategory(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("day") int day
    );
}