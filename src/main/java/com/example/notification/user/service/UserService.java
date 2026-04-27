package com.example.notification.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.common.jwt.JwtTokenProvider;
import com.example.notification.user.dto.UserLoginRequest;
import com.example.notification.user.dto.UserLoginResponse;
import com.example.notification.user.dto.UserResponse;
import com.example.notification.user.dto.UserSignupRequest;
import com.example.notification.user.entity.User;
import com.example.notification.user.repository.UserRepository;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public UserResponse signup(UserSignupRequest request) {
        log.info("Signup requested. email={}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            log.warn("Signup failed. reason=duplicate_email, email={}", request.email());
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.create(request.email(), request.name(), passwordHash);
        User savedUser = userRepository.save(user);

        log.info("Signup completed. userId={}, email={}", savedUser.getId(), savedUser.getEmail());

        return UserResponse.from(savedUser);
    }

    @Transactional(readOnly = true)
    public UserLoginResponse login(UserLoginRequest request) {
        log.info("Login requested. email={}", request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login failed. reason=user_not_found, email={}", request.email());
                    return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed. reason=invalid_password, userId={}, email={}", user.getId(), user.getEmail());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserResponse userResponse = UserResponse.from(user);
        String accessToken = jwtTokenProvider.createAccessToken(user);

        log.info("Login completed. userId={}, email={}", user.getId(), user.getEmail());

        return UserLoginResponse.bearer(userResponse, accessToken, jwtTokenProvider.getExpirationSeconds());
    }
}
