package pyc.lopatuxin.investment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.util.List;

public interface SecurityRepository extends JpaRepository<Security, String> {

    List<Security> findByType(SecurityType type);

    List<Security> findAllByHistoryStatus(HistoryStatus status);
}
