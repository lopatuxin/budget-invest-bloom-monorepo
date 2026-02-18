package pyc.lopatuxin.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pyc.lopatuxin.auth.entity.UserRole;

import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
}
