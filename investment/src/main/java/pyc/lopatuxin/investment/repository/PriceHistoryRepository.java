package pyc.lopatuxin.investment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // One row per ticker: its latest close strictly before `date`. Used to seed a value series'
    // starting price for a ticker with no row inside the requested window — a plain "less than
    // date" query would pull that ticker's entire history up to `date` just to keep the last row.
    @Query("SELECT ph FROM PriceHistory ph WHERE ph.ticker IN :tickers AND ph.tradeDate = (" +
           "SELECT MAX(ph2.tradeDate) FROM PriceHistory ph2 " +
           "WHERE ph2.ticker = ph.ticker AND ph2.tradeDate < :date)")
    List<PriceHistory> findLastBeforeDateForTickers(@Param("tickers") Collection<String> tickers, @Param("date") LocalDate date);

    List<PriceHistory> findByTickerAndTradeDateBetweenOrderByTradeDateAsc(String ticker, LocalDate from, LocalDate to);

    Optional<PriceHistory> findFirstByTickerAndTradeDateLessThanEqualOrderByTradeDateDesc(String ticker, LocalDate date);

    List<PriceHistory> findByTickerOrderByTradeDateAsc(String ticker);

    Optional<PriceHistory> findFirstByTickerOrderByTradeDateDesc(String ticker);

    Optional<PriceHistory> findFirstByTickerOrderByTradeDateAsc(String ticker);
}
