package com.example.notification.template.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.example.notification.notification.entity.NotificationChannel;
import com.example.notification.notification.entity.NotificationType;
import com.example.notification.template.entity.NotificationTemplate;
import com.example.notification.template.repository.NotificationTemplateRepository;

@Configuration
@Profile("!test")
public class NotificationTemplateSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationTemplateSeedConfig.class);

    @Bean
    public ApplicationRunner notificationTemplateSeeder(NotificationTemplateRepository templateRepository) {
        return args -> {
            seedIfMissing(
                    templateRepository,
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.EMAIL,
                    "Payment confirmed",
                    "Your payment has been confirmed."
            );
            seedIfMissing(
                    templateRepository,
                    NotificationType.PAYMENT_CONFIRMED,
                    NotificationChannel.IN_APP,
                    "Payment confirmed",
                    "Your payment has been confirmed."
            );
            seedIfMissing(
                    templateRepository,
                    NotificationType.COURSE_APPLIED,
                    NotificationChannel.EMAIL,
                    "Course application received",
                    "Your course application has been received."
            );
            seedIfMissing(
                    templateRepository,
                    NotificationType.COURSE_APPLIED,
                    NotificationChannel.IN_APP,
                    "Course application received",
                    "Your course application has been received."
            );
            seedIfMissing(
                    templateRepository,
                    NotificationType.COURSE_START_REMINDER,
                    NotificationChannel.EMAIL,
                    "Course starts soon",
                    "Your course is about to start."
            );
            seedIfMissing(
                    templateRepository,
                    NotificationType.COURSE_START_REMINDER,
                    NotificationChannel.IN_APP,
                    "Course starts soon",
                    "Your course is about to start."
            );
            seedIfMissing(
                    templateRepository,
                    NotificationType.COURSE_CANCELLED,
                    NotificationChannel.EMAIL,
                    "Course cancelled",
                    "Your course has been cancelled."
            );
            seedIfMissing(
                    templateRepository,
                    NotificationType.COURSE_CANCELLED,
                    NotificationChannel.IN_APP,
                    "Course cancelled",
                    "Your course has been cancelled."
            );
        };
    }

    private void seedIfMissing(
            NotificationTemplateRepository templateRepository,
            NotificationType type,
            NotificationChannel channel,
            String title,
            String body
    ) {
        if (templateRepository.findByTypeAndChannel(type, channel).isPresent()) {
            return;
        }

        NotificationTemplate template = NotificationTemplate.create(type, channel, title, body, true);
        templateRepository.save(template);
        log.info("Seeded notification template. type={}, channel={}", type, channel);
    }
}
