package pyc.lopatuxin.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pyc.lopatuxin.auth.dto.request.RegisterRequest;
import pyc.lopatuxin.auth.dto.response.RegisterResponse;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    public RegisterResponse register(RegisterRequest request) {
        return RegisterResponse.builder()
                .userId(UUID.randomUUID())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .isActive(true)
                .isVerified(false)
                .createdAt(LocalDateTime.now())
                .build();
    }
}