package pyc.lopatuxin.budget.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pyc.lopatuxin.budget.entity.CapitalRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с записями капитала пользователя.
 */
public interface CapitalRecordRepository extends JpaRepository<CapitalRecord, UUID> {

    /**
     * Ищет запись капитала пользователя за конкретный месяц и год.
     *
     * @param userId идентификатор пользователя
     * @param month  номер месяца (1-12)
     * @param year   год
     * @return Optional с найденной записью капитала, или пустой если запись отсутствует
     */
    Optional<CapitalRecord> findByUserIdAndMonthAndYear(UUID userId, Integer month, Integer year);

    /**
     * Возвращает последние известные записи капитала для пользователя,
     * отсортированные по году и месяцу в убывающем порядке.
     * Используется для получения актуального значения капитала, если за
     * запрошенный месяц запись отсутствует.
     *
     * @param userId   идентификатор пользователя
     * @param pageable параметры страницы (для ограничения результата одной записью)
     * @return список записей (обычно одна — самая последняя)
     */
    @Query("""
            SELECT cr
            FROM CapitalRecord cr
            WHERE cr.userId = :userId
            ORDER BY cr.year DESC, cr.month DESC
            """)
    List<CapitalRecord> findLatestByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Возвращает последнюю известную запись капитала пользователя не позднее указанного месяца и года.
     * Используется для тренда капитала: сравнение опирается на фактическое последнее известное
     * значение на указанный период, а не на ноль, если записи именно за этот месяц нет.
     *
     * @param userId   идентификатор пользователя
     * @param year     год, не позднее которого искать запись
     * @param month    месяц (в пределах {@code year}), не позднее которого искать запись
     * @param pageable параметры страницы (для ограничения результата одной записью)
     * @return список записей (обычно одна — самая последняя не позднее указанного периода)
     */
    @Query("""
            SELECT cr
            FROM CapitalRecord cr
            WHERE cr.userId = :userId
              AND (cr.year < :year OR (cr.year = :year AND cr.month <= :month))
            ORDER BY cr.year DESC, cr.month DESC
            """)
    List<CapitalRecord> findLatestByUserIdAsOf(@Param("userId") UUID userId,
                                               @Param("year") Integer year,
                                               @Param("month") Integer month,
                                               Pageable pageable);

    /**
     * Возвращает помесячные суммы капитала пользователя за указанный год.
     * Каждый элемент массива содержит номер месяца и сумму капитала.
     *
     * @param userId идентификатор пользователя
     * @param year   календарный год
     * @return список пар [месяц, сумма] отсортированных по месяцу
     */
    @Query("""
            SELECT cr.month, cr.amount
            FROM CapitalRecord cr
            WHERE cr.userId = :userId AND cr.year = :year
            ORDER BY cr.month
            """)
    List<Object[]> findMonthlyCapitalByUserIdAndYear(
            @Param("userId") UUID userId,
            @Param("year") int year
    );
}