package com.example.notification.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.notification.dispatchhistory.entity.DispatchStatus;
import com.example.notification.dispatchhistory.entity.NotificationDispatchHistory;
import com.example.notification.dispatchhistory.repository.NotificationDispatchHistoryRepository;
import com.example.notification.notification.entity.Notification;
import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationType;
import com.example.notification.template.entity.NotificationTemplate;
import com.example.notification.template.repository.NotificationTemplateRepository;
import com.example.notification.user.entity.User;
import com.example.notification.user.entity.UserStatus;
import com.example.notification.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class NotificationJpaMappingTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDispatchHistoryRepository dispatchHistoryRepository;

    @Autowired
    private NotificationTemplateRepository templateRepository;

    @Test
    void persistEntitiesFollowingReadmeSchema() {
        User user = userRepository.save(User.create("user@test.com", "tester", UserStatus.ACTIVE));
        NotificationTemplate template = templateRepository.save(
                NotificationTemplate.create(
                        NotificationType.PAYMENT_CONFIRMED,
                        NotificationChannel.EMAIL,
                        "Payment completed",
                        "Payment has been completed.",
                        true
                )
        );
        Notification notification = notificationRepository.save(
                Notification.createPending(
                        user,
                        NotificationType.PAYMENT_CONFIRMED,
                        NotificationChannel.EMAIL,
                        "101:PAYMENT_CONFIRMED:EMAIL:payment-5001",
                        "{\"eventId\":\"payment-5001\"}",
                        3,
                        LocalDateTime.of(2026, 4, 27, 10, 0)
                )
        );
        NotificationDispatchHistory history = dispatchHistoryRepository.save(
                NotificationDispatchHistory.create(
                        notification,
                        1,
                        DispatchStatus.SUCCESS,
                        null,
                        null,
                        LocalDateTime.of(2026, 4, 27, 10, 1)
                )
        );

        assertThat(user.getId()).isNotNull();
        assertThat(template).isNotNull();
        assertThat(notification.getId()).isNotNull();
        assertThat(history).isNotNull();
        assertThat(notificationRepository.findByDedupKey("101:PAYMENT_CONFIRMED:EMAIL:payment-5001")).isPresent();
    }
}
