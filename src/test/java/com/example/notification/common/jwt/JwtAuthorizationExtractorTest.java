package com.example.notification.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;

class JwtAuthorizationExtractorTest {

    @Test
    @DisplayName("Bearer 헤더에서 JWT 토큰을 추출하고 검증한다")
    void extractValidatesBearerToken() {
        JwtTokenProvider jwtTokenProvider = org.mockito.Mockito.mock(JwtTokenProvider.class);
        JwtAuthorizationExtractor extractor = new JwtAuthorizationExtractor(jwtTokenProvider);
        JwtClaims expectedClaims = new JwtClaims(1L, "user@test.com", "tester", 1L, 2L);
        when(jwtTokenProvider.validateAccessToken("valid-token")).thenReturn(expectedClaims);

        JwtClaims claims = extractor.extract("Bearer valid-token");

        assertThat(claims).isEqualTo(expectedClaims);
        verify(jwtTokenProvider).validateAccessToken("valid-token");
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증에 실패한다")
    void extractFailsWhenAuthorizationHeaderIsMissing() {
        JwtAuthorizationExtractor extractor = new JwtAuthorizationExtractor(org.mockito.Mockito.mock(JwtTokenProvider.class));

        assertThatThrownBy(() -> extractor.extract(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 인증에 실패한다")
    void extractFailsWhenAuthorizationHeaderIsNotBearer() {
        JwtAuthorizationExtractor extractor = new JwtAuthorizationExtractor(org.mockito.Mockito.mock(JwtTokenProvider.class));

        assertThatThrownBy(() -> extractor.extract("Basic token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }
}
