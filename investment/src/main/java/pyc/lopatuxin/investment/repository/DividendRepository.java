package pyc.lopatuxin.investment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    boolean existsBySecurity_Ticker(String ticker);

    List<Dividend> findBySecurity_TickerOrderByRecordDateDesc(String ticker);

    boolean existsBySecurity_TickerAndRecordDate(String ticker, LocalDate recordDate);

    Optional<Dividend> findBySecurity_TickerAndRecordDate(String ticker, LocalDate recordDate);

    // "Received" is paymentDate when known, falling back to recordDate otherwise (see
    // PortfolioService — the same rule drives the "12 months" sum, the recentDividends list and
    // dividendYieldPercent). Currency is not filtered here: a foreign-currency dividend still
    // belongs in the displayed list (see UpcomingDividendDto), it is only excluded from the RUB
    // sum, which PortfolioService does in memory. The upper bound is exclusive: a receipt date of
    // "today" belongs to upcomingDividends only (see findUpcomingByTickersWithSecurity below).
    @Query("SELECT d FROM Dividend d JOIN FETCH d.security " +
           "WHERE d.security.ticker IN :tickers " +
           "AND COALESCE(d.paymentDate, d.recordDate) >= :from AND COALESCE(d.paymentDate, d.recordDate) < :to " +
           "AND d.status <> pyc.lopatuxin.investment.entity.enums.DividendStatus.CANCELLED " +
           "ORDER BY COALESCE(d.paymentDate, d.recordDate) DESC")
    List<Dividend> findByTickerInAndReceivedDateBetweenWithSecurity(@Param("tickers") Collection<String> tickers, @Param("from") LocalDate from, @Param("to") LocalDate to);

    // One atomic UPDATE instead of loading every due ANNOUNCED row as an entity just to flip
    // one field and save it back — no N+1, no lost-update race against a concurrent read.
    @Modifying
    @Query("UPDATE Dividend d SET d.status = pyc.lopatuxin.investment.entity.enums.DividendStatus.PAID " +
           "WHERE d.status = pyc.lopatuxin.investment.entity.enums.DividendStatus.ANNOUNCED " +
           "AND d.recordDate < :today")
    int markPastRecordDatesAsPaid(@Param("today") LocalDate today);

    // A row qualifies as upcoming by either date independently (recordDate not yet reached, or
    // paymentDate known and not yet reached) — see PortfolioService.buildUpcomingDividends, which
    // also derives the display/sort date from the same two rules (pt. 21 of the plan).
    @Query("SELECT d FROM Dividend d JOIN FETCH d.security " +
           "WHERE d.security.ticker IN :tickers " +
           "AND (d.recordDate >= :today OR d.paymentDate >= :today) " +
           "AND d.status <> pyc.lopatuxin.investment.entity.enums.DividendStatus.CANCELLED")
    List<Dividend> findUpcomingByTickersWithSecurity(
            @Param("tickers") Collection<String> tickers,
            @Param("today") LocalDate today);
}
