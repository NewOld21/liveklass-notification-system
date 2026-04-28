package com.example.notification.common.jwt;

import org.springframework.stereotype.Component;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;

@Component
public class JwtAuthorizationExtractor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthorizationExtractor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public JwtClaims extract(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Authorization header is required.");
        }
        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Authorization header must use Bearer token.");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Bearer token is required.");
        }

        return jwtTokenProvider.validateAccessToken(token);
    }
}
