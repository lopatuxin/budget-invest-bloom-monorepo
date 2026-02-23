package pyc.lopatuxin.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pyc.lopatuxin.budget.entity.Income;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с доходами пользователя.
 */
public interface IncomeRepository extends JpaRepository<Income, UUID> {

    /**
     * Возвращает суммарные доходы пользователя за указанный диапазон дат.
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
            """)
    Optional<BigDecimal> sumAmountByUserIdAndDateBetween(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}