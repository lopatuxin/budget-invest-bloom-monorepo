package pyc.lopatuxin.investment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pyc.lopatuxin.investment.entity.Transaction;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByUserId(UUID userId);

    List<Transaction> findByUserIdAndSecurity_Ticker(UUID userId, String ticker);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.security WHERE t.userId = :userId")
    List<Transaction> findByUserIdWithSecurity(@Param("userId") UUID userId);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.security WHERE t.userId = :userId AND t.security.ticker = :ticker ORDER BY t.executedAt DESC")
    List<Transaction> findByUserIdAndTickerWithSecurity(@Param("userId") UUID userId, @Param("ticker") String ticker);
}
