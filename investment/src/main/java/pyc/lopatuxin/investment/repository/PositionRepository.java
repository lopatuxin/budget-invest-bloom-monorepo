package pyc.lopatuxin.investment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    List<Position> findByUserId(UUID userId);

    Optional<Position> findByUserIdAndSecurity_Ticker(UUID userId, String ticker);

    boolean existsByUserIdAndSecurity_Ticker(UUID userId, String ticker);

    @Query("select distinct p.security.ticker from Position p")
    List<String> findActiveTickers();

    @Query("SELECT p FROM Position p JOIN FETCH p.security WHERE p.userId = :userId")
    List<Position> findByUserIdWithSecurity(@Param("userId") UUID userId);

    // BondRedemptionService's candidate pool: every position in a security type that can be
    // redeemed, across all users, with its Security fetched for the maturity-date check.
    @Query("SELECT p FROM Position p JOIN FETCH p.security s WHERE s.type IN :types")
    List<Position> findBySecurity_TypeIn(@Param("types") Collection<SecurityType> types);
}
