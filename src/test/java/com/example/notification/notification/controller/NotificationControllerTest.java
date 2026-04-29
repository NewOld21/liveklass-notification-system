package com.example.notification.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.common.jwt.JwtAuthorizationExtractor;
import com.example.notification.common.jwt.JwtClaims;
import com.example.notification.notification.dto.NotificationCreateRequest;
import com.example.notification.notification.dto.NotificationCreateResponse;
import com.example.notification.notification.dto.NotificationStatusResponse;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationStatus;
import com.example.notification.notification.entity.NotificationType;
import com.example.notification.notification.service.NotificationService;

class NotificationControllerTest {

    @Test
    @DisplayName("알림 생성 API는 JWT 헤더를 검증하고 CREATED 상태를 반환한다")
    void createReturnsCreatedResponse() {
        NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
        JwtAuthorizationExtractor jwtAuthorizationExtractor = org.mockito.Mockito.mock(JwtAuthorizationExtractor.class);
        NotificationController controller = new NotificationController(notificationService, jwtAuthorizationExtractor);
        NotificationCreateRequest request = createRequest(101L);
        NotificationCreateResponse serviceResponse = new NotificationCreateResponse(
                1L,
                NotificationStatus.PENDING,
                LocalDateTime.of(2026, 4, 24, 10, 0)
        );
        when(jwtAuthorizationExtractor.extract("Bearer valid-token"))
                .thenReturn(new JwtClaims(101L, "user@test.com", "tester", 1L, 2L));
        when(notificationService.create(request)).thenReturn(serviceResponse);

        ResponseEntity<NotificationCreateResponse> response = controller.create("Bearer valid-token", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
        verify(jwtAuthorizationExtractor).extract("Bearer valid-token");
        verify(notificationService).create(request);
    }

    @Test
    @DisplayName("JWT 사용자와 수신자가 다르면 알림 생성에 실패한다")
    void createFailsWhenAuthenticatedUserDoesNotMatchRecipient() {
        NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
        JwtAuthorizationExtractor jwtAuthorizationExtractor = org.mockito.Mockito.mock(JwtAuthorizationExtractor.class);
        NotificationController controller = new NotificationController(notificationService, jwtAuthorizationExtractor);
        NotificationCreateRequest request = createRequest(101L);
        when(jwtAuthorizationExtractor.extract("Bearer valid-token"))
                .thenReturn(new JwtClaims(202L, "other@test.com", "other", 1L, 2L));

        assertThatThrownBy(() -> controller.create("Bearer valid-token", request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(notificationService, never()).create(request);
    }

    @Test
    @DisplayName("알림 상태 조회 API는 JWT userId로 상태 조회 서비스를 호출한다")
    void getStatusUsesAuthenticatedUserId() {
        NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
        JwtAuthorizationExtractor jwtAuthorizationExtractor = org.mockito.Mockito.mock(JwtAuthorizationExtractor.class);
        NotificationController controller = new NotificationController(notificationService, jwtAuthorizationExtractor);
        NotificationStatusResponse serviceResponse = new NotificationStatusResponse(
                1L,
                101L,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                NotificationStatus.PENDING,
                0,
                null,
                null,
                null
        );
        when(jwtAuthorizationExtractor.extract("Bearer valid-token"))
                .thenReturn(new JwtClaims(101L, "user@test.com", "tester", 1L, 2L));
        when(notificationService.getStatus(1L, 101L)).thenReturn(serviceResponse);

        ResponseEntity<NotificationStatusResponse> response = controller.getStatus("Bearer valid-token", 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
        verify(notificationService).getStatus(1L, 101L);
    }

    @Test
    @DisplayName("읽음 처리 API는 JWT userId로 읽음 처리 서비스를 호출한다")
    void markAsReadUsesAuthenticatedUserId() {
        NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
        JwtAuthorizationExtractor jwtAuthorizationExtractor = org.mockito.Mockito.mock(JwtAuthorizationExtractor.class);
        NotificationController controller = new NotificationController(notificationService, jwtAuthorizationExtractor);
        when(jwtAuthorizationExtractor.extract("Bearer valid-token"))
                .thenReturn(new JwtClaims(101L, "user@test.com", "tester", 1L, 2L));

        ResponseEntity<Void> response = controller.markAsRead("Bearer valid-token", 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(notificationService).markAsRead(1L, 101L);
    }

    private NotificationCreateRequest createRequest(Long recipientId) {
        return new NotificationCreateRequest(
                recipientId,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                Map.of("eventId", "payment-5001")
        );
    }
}
