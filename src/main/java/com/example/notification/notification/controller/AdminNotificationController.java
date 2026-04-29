package com.example.notification.notification.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.notification.notification.dto.AdminFailedNotificationResponse;
import com.example.notification.notification.dto.NotificationStatusResponse;
import com.example.notification.notification.service.AdminNotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/notifications")
@Tag(name = "Admin Notifications", description = "Admin notification operation APIs")
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    public AdminNotificationController(AdminNotificationService adminNotificationService) {
        this.adminNotificationService = adminNotificationService;
    }

    @GetMapping("/failed")
    @Operation(summary = "List exhausted failed notifications")
    public ResponseEntity<List<AdminFailedNotificationResponse>> getExhaustedFailedNotifications(
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(adminNotificationService.getExhaustedFailedNotifications(size));
    }

    @PatchMapping("/{notificationId}/manual-retry")
    @Operation(summary = "Reopen failed notification for manual retry")
    public ResponseEntity<NotificationStatusResponse> reopenForManualRetry(@PathVariable Long notificationId) {
        return ResponseEntity.ok(adminNotificationService.reopenForManualRetry(notificationId));
    }
}
