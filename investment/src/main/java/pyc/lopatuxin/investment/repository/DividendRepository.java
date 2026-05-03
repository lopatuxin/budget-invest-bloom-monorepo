package pyc.lopatuxin.investment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pyc.lopatuxin.investment.entity.Dividend;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DividendRepository extends JpaRepository<Dividend, UUID> {

    List<Dividend> findBySecurity_Ticker(String ticker);

    List<Dividend> findBySecurity_TickerInAndPaymentDateBetween(Collection<String> tickers, LocalDate from, LocalDate to);

    boolean existsBySecurity_TickerAndRecordDate(String ticker, LocalDate recordDate);

    Optional<Dividend> findBySecurity_TickerAndRecordDate(String ticker, LocalDate recordDate);

    List<Dividend> findBySecurity_TickerAndPaymentDateAfter(String ticker, LocalDate after);

    @Query("SELECT d FROM Dividend d JOIN FETCH d.security WHERE d.security.ticker IN :tickers AND d.paymentDate BETWEEN :from AND :to")
    List<Dividend> findByTickerInAndPaymentDateBetweenWithSecurity(@Param("tickers") Collection<String> tickers, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT d FROM Dividend d JOIN FETCH d.security " +
           "WHERE d.security.ticker IN :tickers " +
           "AND d.recordDate >= :from " +
           "AND d.status <> pyc.lopatuxin.investment.entity.enums.DividendStatus.CANCELLED " +
           "ORDER BY d.recordDate ASC")
    List<Dividend> findUpcomingByTickersWithSecurity(
            @Param("tickers") Collection<String> tickers,
            @Param("from") LocalDate from);
}
