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
     * Возвращает суммарные доходы пользователя за всё время.
     *
     * @param userId идентификатор пользователя
     * @return сумма всех доходов (0 если записей нет)
     */
    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i WHERE i.userId = :userId")
    BigDecimal sumByUserId(@Param("userId") UUID userId);

    /**
     * Возвращает помесячные суммы доходов пользователя за указанный год.
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
            GROUP BY MONTH(i.date)
            ORDER BY MONTH(i.date)
            """)
    List<Object[]> findMonthlyIncomeByUserIdAndYear(
            @Param("userId") UUID userId,
            @Param("year") int year
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
}