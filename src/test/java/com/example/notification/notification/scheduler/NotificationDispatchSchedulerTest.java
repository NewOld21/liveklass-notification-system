package com.example.notification.notification.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import com.example.notification.notification.repository.NotificationRepository;
import com.example.notification.notification.service.NotificationDispatchService;

class NotificationDispatchSchedulerTest {

    @Test
    @DisplayName("PENDING과 재시도 시간이 지난 RETRY_WAITING 알림을 dispatch 한다")
    void dispatchPendingAndRetryWaitingNotifications() {
        NotificationRepository notificationRepository = org.mockito.Mockito.mock(NotificationRepository.class);
        NotificationDispatchService dispatchService = org.mockito.Mockito.mock(NotificationDispatchService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        NotificationDispatchScheduler scheduler = new NotificationDispatchScheduler(
                notificationRepository,
                dispatchService,
                clock,
                50
        );
        PageRequest limit = PageRequest.of(0, 50);
        when(notificationRepository.findStaleProcessingTargetIds(
                java.time.LocalDateTime.of(2026, 4, 24, 9, 50),
                limit
        )).thenReturn(List.of(9L));
        when(notificationRepository.findPendingDispatchTargetIds(limit)).thenReturn(List.of(1L, 2L));
        when(notificationRepository.findRetryWaitingDispatchTargetIds(
                java.time.LocalDateTime.of(2026, 4, 24, 10, 0),
                limit
        )).thenReturn(List.of(3L));

        scheduler.dispatchPendingAndRetryWaitingNotifications();

        verify(dispatchService).recoverStaleProcessing(9L);
        verify(dispatchService).dispatch(1L);
        verify(dispatchService).dispatch(2L);
        verify(dispatchService).dispatch(3L);
    }

    @Test
    @DisplayName("특정 알림 dispatch 중 예외가 발생해도 다음 알림을 계속 처리한다")
    void dispatchContinuesWhenSingleNotificationFailsUnexpectedly() {
        NotificationRepository notificationRepository = org.mockito.Mockito.mock(NotificationRepository.class);
        NotificationDispatchService dispatchService = org.mockito.Mockito.mock(NotificationDispatchService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        NotificationDispatchScheduler scheduler = new NotificationDispatchScheduler(
                notificationRepository,
                dispatchService,
                clock,
                50
        );
        PageRequest limit = PageRequest.of(0, 50);
        when(notificationRepository.findStaleProcessingTargetIds(
                java.time.LocalDateTime.of(2026, 4, 24, 9, 50),
                limit
        )).thenReturn(List.of());
        when(notificationRepository.findPendingDispatchTargetIds(limit)).thenReturn(List.of(1L, 2L));
        when(notificationRepository.findRetryWaitingDispatchTargetIds(
                java.time.LocalDateTime.of(2026, 4, 24, 10, 0),
                limit
        )).thenReturn(List.of());
        when(dispatchService.dispatch(1L)).thenThrow(new RuntimeException("unexpected"));

        scheduler.dispatchPendingAndRetryWaitingNotifications();

        verify(dispatchService).dispatch(1L);
        verify(dispatchService).dispatch(2L);
    }
}
