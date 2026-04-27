package com.example.notification.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.user.entity.User;

class JwtTokenProviderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-04-27T00:00:00Z"),
            ZoneId.of("UTC")
    );

    @Test
    @DisplayName("JWT 토큰을 생성하고 검증할 수 있다")
    void validateAccessTokenReturnsClaims() {
        JwtTokenProvider provider = new JwtTokenProvider(FIXED_CLOCK, "test-secret", 3600);
        User user = createUserWithId();

        String token = provider.createAccessToken(user);
        JwtClaims claims = provider.validateAccessToken(token);

        assertThat(claims.email()).isEqualTo("user@test.com");
        assertThat(claims.name()).isEqualTo("tester");
        assertThat(claims.issuedAt()).isEqualTo(1777248000L);
        assertThat(claims.expiresAt()).isEqualTo(1777251600L);
    }

    @Test
    @DisplayName("서명이 변조된 JWT 토큰이면 검증에 실패한다")
    void validateAccessTokenFailsWhenSignatureIsInvalid() {
        JwtTokenProvider provider = new JwtTokenProvider(FIXED_CLOCK, "test-secret", 3600);
        User user = createUserWithId();
        String token = provider.createAccessToken(user);
        String tamperedToken = token.substring(0, token.length() - 1) + "x";

        assertThatThrownBy(() -> provider.validateAccessToken(tamperedToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("만료된 JWT 토큰이면 검증에 실패한다")
    void validateAccessTokenFailsWhenTokenIsExpired() {
        JwtTokenProvider provider = new JwtTokenProvider(FIXED_CLOCK, "test-secret", -1);
        User user = createUserWithId();
        String token = provider.createAccessToken(user);

        assertThatThrownBy(() -> provider.validateAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    @DisplayName("형식이 잘못된 JWT 토큰이면 검증에 실패한다")
    void validateAccessTokenFailsWhenTokenFormatIsInvalid() {
        JwtTokenProvider provider = new JwtTokenProvider(FIXED_CLOCK, "test-secret", 3600);

        assertThatThrownBy(() -> provider.validateAccessToken("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    private User createUserWithId() {
        User user = User.create("user@test.com", "tester", "password-hash");
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}