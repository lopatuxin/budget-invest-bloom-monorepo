package pyc.lopatuxin.investment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.PriceHistoryId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, PriceHistoryId> {

    List<PriceHistory> findByTickerAndTradeDateBetween(String ticker, LocalDate from, LocalDate to);

    boolean existsByTicker(String ticker);

    List<PriceHistory> findByTickerInAndTradeDateBetweenOrderByTradeDateAsc(Collection<String> tickers, LocalDate from, LocalDate to);

    List<PriceHistory> findByTickerAndTradeDateBetweenOrderByTradeDateAsc(String ticker, LocalDate from, LocalDate to);

    Optional<PriceHistory> findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(String ticker, LocalDate date);
}
