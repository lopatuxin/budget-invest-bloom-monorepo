package pyc.lopatuxin.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.auth.dto.request.LoginRequest;
import pyc.lopatuxin.auth.dto.response.LoginResponse;
import pyc.lopatuxin.auth.dto.response.UserDto;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginService {

    public LoginResponse login(LoginRequest request) {
        UserDto userDto = UserDto.builder()
                .userId("123e4567-e89b-12d3-a456-426614174000")
                .email(request.getEmail())
                .isActive(true)
                .isVerified(true)
                .roles(List.of("USER"))
                .lastLoginAt(LocalDateTime.now())
                .build();

        return LoginResponse.builder()
                .accessToken("sample.access.token")
                .refreshToken("sample.refresh.token")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(userDto)
                .build();
    }
}