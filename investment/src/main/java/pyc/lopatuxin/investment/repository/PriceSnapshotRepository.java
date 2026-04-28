package pyc.lopatuxin.investment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pyc.lopatuxin.investment.entity.PriceSnapshot;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, String> {
}
