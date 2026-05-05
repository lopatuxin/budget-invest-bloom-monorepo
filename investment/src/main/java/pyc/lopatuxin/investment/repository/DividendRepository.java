package pyc.lopatuxin.investment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pyc.lopatuxin.investment.entity.Dividend;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DividendRepository extends JpaRepository<Dividend, UUID> {

    List<Dividend> findBySecurity_Ticker(String ticker);

    boolean existsBySecurity_TickerAndRecordDate(String ticker, LocalDate recordDate);

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

    @Query("SELECT d FROM Dividend d JOIN FETCH d.security " +
           "WHERE d.security.ticker = :ticker " +
           "AND d.status = pyc.lopatuxin.investment.entity.enums.DividendStatus.PAID " +
           "ORDER BY d.paymentDate DESC")
    List<Dividend> findPaidByTickerWithSecurity(@Param("ticker") String ticker);
}
