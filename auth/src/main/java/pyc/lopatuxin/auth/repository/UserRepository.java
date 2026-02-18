package pyc.lopatuxin.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pyc.lopatuxin.auth.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findUserByEmail(String email);
}
