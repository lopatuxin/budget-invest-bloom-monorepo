package pyc.lopatuxin.investment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
