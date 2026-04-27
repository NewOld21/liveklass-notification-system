package com.example.notification.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.user.dto.UserLoginRequest;
import com.example.notification.user.dto.UserLoginResponse;
import com.example.notification.user.dto.UserResponse;
import com.example.notification.user.dto.UserSignupRequest;
import com.example.notification.user.entity.User;
import com.example.notification.user.repository.UserRepository;

@Service
public class UserService {

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
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "Email already exists.");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.create(request.email(), request.name(), passwordHash);

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserLoginResponse login(UserLoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid email or password.");
        }

        UserResponse userResponse = UserResponse.from(user);
        String accessToken = jwtTokenProvider.createAccessToken(user);

        return UserLoginResponse.bearer(userResponse, accessToken, jwtTokenProvider.getExpirationSeconds());
    }
}
