package pyc.lopatuxin.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.auth.config.JwtConfig;
import pyc.lopatuxin.auth.dto.request.LoginRequest;
import pyc.lopatuxin.auth.dto.response.LoginResponse;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.mapper.UserMapper;
import pyc.lopatuxin.auth.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final JwtConfig jwtConfig;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Optional<User> optionalUser = userRepository.findUserByEmail(request.getEmail());

        if (optionalUser.isEmpty()) {
            throw new BadCredentialsException("Неверный email или пароль");
        }

        User user = optionalUser.get();
        LocalDateTime lockedUntil = user.getLockedUntil();

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new AccessDeniedException("Пользователь не активирован");
        }

        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            throw new AccessDeniedException("Аккаунт заблокирован до " + user.getLockedUntil());
        }

        if (lockedUntil != null && lockedUntil.isBefore(LocalDateTime.now())) {
            resetFailedAttempts(user);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new BadCredentialsException("Неверный email или пароль");
        }

        resetFailedAttempts(user);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        int expiresIn = (int) (jwtConfig.getAccessTokenExpiration() / 1000);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .user(userMapper.toUserDto(user))
                .build();
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
        }

        userRepository.save(user);
    }

    private void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }
}