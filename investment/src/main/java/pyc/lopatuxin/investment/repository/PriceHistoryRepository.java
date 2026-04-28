package pyc.lopatuxin.investment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pyc.lopatuxin.investment.entity.PriceHistory;
import pyc.lopatuxin.investment.entity.PriceHistoryId;

import java.time.LocalDate;
import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, PriceHistoryId> {

    List<PriceHistory> findByTickerAndTradeDateBetween(String ticker, LocalDate from, LocalDate to);
}
