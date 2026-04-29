package com.example.notification.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.notification.common.jwt.JwtAuthorizationExtractor;
import com.example.notification.common.jwt.JwtClaims;
import com.example.notification.notification.dto.NotificationListItemResponse;
import com.example.notification.notification.dto.NotificationReadFilter;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationStatus;
import com.example.notification.notification.entity.NotificationType;
import com.example.notification.notification.service.NotificationService;

class UserNotificationControllerTest {

    @Test
    @DisplayName("사용자 알림 목록 API는 JWT userId와 필터를 서비스에 전달한다")
    void getUserNotificationsUsesAuthenticatedUserIdAndFilter() {
        NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
        JwtAuthorizationExtractor jwtAuthorizationExtractor = org.mockito.Mockito.mock(JwtAuthorizationExtractor.class);
        UserNotificationController controller = new UserNotificationController(
                notificationService,
                jwtAuthorizationExtractor
        );
        List<NotificationListItemResponse> serviceResponse = List.of(new NotificationListItemResponse(
                1L,
                101L,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationChannel.EMAIL,
                NotificationStatus.PENDING,
                LocalDateTime.of(2026, 4, 24, 10, 0),
                null
        ));
        when(jwtAuthorizationExtractor.extract("Bearer valid-token"))
                .thenReturn(new JwtClaims(101L, "user@test.com", "tester", 1L, 2L));
        when(notificationService.getUserNotifications(101L, 101L, NotificationReadFilter.UNREAD))
                .thenReturn(serviceResponse);

        ResponseEntity<List<NotificationListItemResponse>> response = controller.getUserNotifications(
                "Bearer valid-token",
                NotificationReadFilter.UNREAD
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
        verify(notificationService).getUserNotifications(101L, 101L, NotificationReadFilter.UNREAD);
    }
}
