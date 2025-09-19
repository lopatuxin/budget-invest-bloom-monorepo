package pyc.lopatuxin.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.auth.dto.request.RegisterRequest;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.entity.UserRole;
import pyc.lopatuxin.auth.enums.RoleName;
import pyc.lopatuxin.auth.exception.UserAlreadyExistsException;
import pyc.lopatuxin.auth.mapper.UserMapper;
import pyc.lopatuxin.auth.repository.UserRepository;
import pyc.lopatuxin.auth.repository.UserRoleRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public void register(RegisterRequest request) {
        Optional<User> optionalUser = userRepository.findUserByEmail(request.getEmail());

        if (optionalUser.isPresent()) {
            throw new UserAlreadyExistsException("Пользователь с такой почтой уже существует");
        }

        User newUser = userMapper.toUser(request);
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setIsActive(true);
        newUser.setIsVerified(false);

        User savedUser = userRepository.save(newUser);
        saveUserRole(savedUser);
    }

    private void saveUserRole(User user) {
        UserRole userRole = UserRole.builder()
                .user(user)
                .roleName(RoleName.USER)
                .build();

        userRoleRepository.save(userRole);
    }
}