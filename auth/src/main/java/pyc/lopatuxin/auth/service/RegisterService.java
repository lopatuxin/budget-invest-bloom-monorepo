package pyc.lopatuxin.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.auth.dto.request.RegisterRequest;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.entity.UserRole;
import pyc.lopatuxin.auth.enums.RoleName;
import pyc.lopatuxin.auth.exception.UserAlreadyExistsException;
import pyc.lopatuxin.auth.mapper.UserMapper;
import pyc.lopatuxin.auth.repository.UserRepository;
import pyc.lopatuxin.auth.repository.UserRoleRepository;

@Service
@RequiredArgsConstructor
public class RegisterService {
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    // @Transactional so the user row and its role commit or roll back together — previously a
    // failure while saving the role left a user with no role row at all, and JwtService.
    // generateAccessToken treats that as a data-integrity fault, so such a user could no longer
    // log in. The existsByEmail check above is only a fast pre-check for the common case; the
    // unique index on users.email is what actually prevents two concurrent requests for the same
    // address both passing it — the catch below turns that race into the same 409 a sequential
    // duplicate gets, instead of a raw 500.
    @Transactional("authTransactionManager")
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Пользователь с такой почтой уже существует");
        }

        User newUser = userMapper.toUser(request);
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setIsActive(true);
        newUser.setIsVerified(false);

        try {
            // saveAndFlush, not save: id is app-generated (@GeneratedValue(UUID)), so a plain
            // save() only queues the INSERT and Hibernate would defer it to the transaction's
            // commit-time flush — after this try block has already returned. Flushing here forces
            // the INSERT (and therefore a concurrent duplicate's unique-index violation) to happen
            // inside the catch's reach.
            User savedUser = userRepository.saveAndFlush(newUser);
            saveUserRole(savedUser);
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyExistsException("Пользователь с такой почтой уже существует");
        }
    }

    private void saveUserRole(User user) {
        UserRole userRole = UserRole.builder()
                .user(user)
                .roleName(RoleName.USER)
                .build();

        userRoleRepository.save(userRole);
    }
}