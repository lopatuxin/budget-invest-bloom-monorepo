package pyc.lopatuxin.investment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pyc.lopatuxin.investment.entity.Position;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    List<Position> findByUserId(UUID userId);

    Optional<Position> findByUserIdAndSecurity_Ticker(UUID userId, String ticker);

    @Query("select distinct p.security.ticker from Position p")
    List<String> findActiveTickers();
}
