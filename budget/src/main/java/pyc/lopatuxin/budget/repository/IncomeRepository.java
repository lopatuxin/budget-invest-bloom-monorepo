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
     * Returns total non-transfer income for the user over all time.
     * Entries with isTransfer=true (investment operations and asset transfers) are excluded.
     *
     * @param userId identifier of the user
     * @return sum of non-transfer incomes (0 if no records)
     */
    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i WHERE i.userId = :userId AND i.isTransfer = false")
    BigDecimal sumNonTransferByUserId(@Param("userId") UUID userId);

    /**
     * Возвращает суммарные не-трансферные доходы пользователя с датой не позже указанной —
     * накопительный итог на начало точки истории капитала. Записи с isTransfer=true исключаются.
     *
     * @param userId идентификатор пользователя
     * @param date   дата, до которой (включительно) считается сумма
     * @return сумма доходов (0 если записей нет)
     */
    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i WHERE i.userId = :userId AND i.date <= :date AND i.isTransfer = false")
    BigDecimal sumNonTransferByUserIdAndDateLessThanEqual(@Param("userId") UUID userId, @Param("date") LocalDate date);

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