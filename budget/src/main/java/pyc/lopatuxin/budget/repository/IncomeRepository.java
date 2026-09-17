package pyc.lopatuxin.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pyc.lopatuxin.budget.entity.Income;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с доходами пользователя.
 */
public interface IncomeRepository extends JpaRepository<Income, UUID> {

    /**
     * Возвращает суммарные не-трансферные доходы пользователя за указанный диапазон дат.
     * Записи с isTransfer=true (инвестиции и переводы между активами) исключаются.
     *
     * @param userId    идентификатор пользователя
     * @param startDate первый день периода (включительно)
     * @param endDate   последний день периода (включительно)
     * @return Optional с суммой доходов, или пустой если записей нет
     */
    @Query("""
            SELECT SUM(i.amount)
            FROM Income i
            WHERE i.userId = :userId
              AND i.date >= :startDate
              AND i.date <= :endDate
              AND i.isTransfer = false
            """)
    Optional<BigDecimal> sumAmountByUserIdAndDateBetween(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Returns total income for the user over all time, including isTransfer=true records.
     * Used only by the overview page's capital section: an investment sale or bond redemption
     * pays isTransfer=true income back out of the portfolio, and that money must reappear as
     * free money there, unlike in every other statistic on the page.
     *
     * @param userId identifier of the user
     * @return sum of all incomes (0 if no records)
     */
    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i WHERE i.userId = :userId")
    BigDecimal sumByUserId(@Param("userId") UUID userId);

    /**
     * Returns total income for the user with a date up to and including the given one, including
     * isTransfer=true records — the cumulative total at the start of a capital history point.
     *
     * @param userId identifier of the user
     * @param date   date up to which (inclusive) the sum is taken
     * @return sum of all incomes (0 if no records)
     */
    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i WHERE i.userId = :userId AND i.date <= :date")
    BigDecimal sumByUserIdAndDateLessThanEqual(@Param("userId") UUID userId, @Param("date") LocalDate date);

    /**
     * Возвращает помесячные суммы не-трансферных доходов пользователя за произвольный диапазон дат
     * (может охватывать несколько лет). Записи с isTransfer=true исключаются.
     *
     * @param userId    идентификатор пользователя
     * @param startDate первый день диапазона (включительно)
     * @param endDate   последний день диапазона (включительно)
     * @return список массивов [year (Integer), month (Integer), сумма (BigDecimal)]
     */
    @Query("""
            SELECT YEAR(i.date), MONTH(i.date), SUM(i.amount)
            FROM Income i
            WHERE i.userId = :userId
              AND i.date >= :startDate
              AND i.date <= :endDate
              AND i.isTransfer = false
            GROUP BY YEAR(i.date), MONTH(i.date)
            """)
    List<Object[]> findMonthlyNonTransferIncomeByUserIdAndDateBetween(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Returns monthly income sums for the user over an arbitrary date range, including
     * isTransfer=true records. Used only for the overview page's capital history, where each
     * month's free-money delta must include investment operations.
     *
     * @param userId    identifier of the user
     * @param startDate first day of the range (inclusive)
     * @param endDate   last day of the range (inclusive)
     * @return list of arrays [year (Integer), month (Integer), sum (BigDecimal)]
     */
    @Query("""
            SELECT YEAR(i.date), MONTH(i.date), SUM(i.amount)
            FROM Income i
            WHERE i.userId = :userId
              AND i.date >= :startDate
              AND i.date <= :endDate
            GROUP BY YEAR(i.date), MONTH(i.date)
            """)
    List<Object[]> findMonthlyIncomeByUserIdAndDateBetween(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Возвращает помесячные суммы не-трансферных доходов пользователя за указанный год.
     * Записи с isTransfer=true (инвестиции и переводы между активами) исключаются.
     *
     * @param userId идентификатор пользователя
     * @param year   календарный год
     * @return список пар [номер месяца (Integer), сумма (BigDecimal)]
     */
    @Query("""
            SELECT MONTH(i.date), SUM(i.amount)
            FROM Income i
            WHERE i.userId = :userId
              AND YEAR(i.date) = :year
              AND i.isTransfer = false
            GROUP BY MONTH(i.date)
            ORDER BY MONTH(i.date)
            """)
    List<Object[]> findMonthlyNonTransferIncomeByUserIdAndYear(
            @Param("userId") UUID userId,
            @Param("year") int year
    );

    /**
     * Returns the date of the user's earliest non-transfer income, used by the analytics page to
     * determine the earliest year with any data. Entries with isTransfer=true are excluded.
     *
     * @param userId identifier of the user
     * @return the earliest date, or empty if the user has no non-transfer incomes
     */
    @Query("SELECT MIN(i.date) FROM Income i WHERE i.userId = :userId AND i.isTransfer = false")
    Optional<LocalDate> findMinNonTransferDateByUserId(@Param("userId") UUID userId);

    /**
     * Возвращает не-трансферные доходы пользователя за месяц, используется лентой операций.
     * Записи с isTransfer=true исключаются.
     *
     * @param userId    идентификатор пользователя
     * @param startDate первый день месяца (включительно)
     * @param endDate   последний день месяца (включительно)
     * @return список доходов месяца
     */
    @Query("""
            SELECT i FROM Income i
            WHERE i.userId = :userId
              AND i.date >= :startDate
              AND i.date <= :endDate
              AND i.isTransfer = false
            """)
    List<Income> findByUserIdAndDateBetweenAndIsTransferFalse(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Возвращает помесячные агрегаты не-трансферных доходов за окно истории: для каждого месяца окна,
     * в котором есть хотя бы одна запись, — сумму записей с датой до дня {@code day} включительно,
     * сумму за полный месяц и число различных дней месяца, на которые приходятся записи (используется
     * для определения границы, с которой у пользователя начался подневный учёт). Месяцы без записей
     * в результат не попадают. Используется для расчёта нормы («обычно к этому дню») по доходам.
     *
     * @param userId    идентификатор пользователя
     * @param startDate первый день окна (включительно)
     * @param endDate   последний день окна (включительно)
     * @param day       день месяца, до которого считается частичная сумма
     * @return список массивов [year (Integer), month (Integer), cutoffSum (BigDecimal), fullSum (BigDecimal), distinctDays (Long)]
     */
    @Query("""
            SELECT YEAR(i.date), MONTH(i.date),
                   SUM(CASE WHEN DAY(i.date) <= :day THEN i.amount ELSE 0 END),
                   SUM(i.amount),
                   COUNT(DISTINCT DAY(i.date))
            FROM Income i
            WHERE i.userId = :userId
              AND i.date >= :startDate
              AND i.date <= :endDate
              AND i.isTransfer = false
            GROUP BY YEAR(i.date), MONTH(i.date)
            """)
    List<Object[]> findWindowedNonTransferIncomeStats(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("day") int day
    );
}