package com.example.notification.notification.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.notification.common.jwt.JwtAuthorizationExtractor;
import com.example.notification.common.jwt.JwtClaims;
import com.example.notification.notification.dto.NotificationListItemResponse;
import com.example.notification.notification.dto.NotificationReadFilter;
import com.example.notification.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/users/me/notifications")
@Tag(name = "User Notifications", description = "User notification list APIs")
public class UserNotificationController {

    private final NotificationService notificationService;
    private final JwtAuthorizationExtractor jwtAuthorizationExtractor;

    public UserNotificationController(
            NotificationService notificationService,
            JwtAuthorizationExtractor jwtAuthorizationExtractor
    ) {
        this.notificationService = notificationService;
        this.jwtAuthorizationExtractor = jwtAuthorizationExtractor;
    }

    @GetMapping
    @Operation(
            summary = "Get user notifications",
            description = "Validates the JWT bearer token and returns the authenticated user's notifications with ALL, READ, or UNREAD filter.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User notifications",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NotificationListItemResponse.class),
                            examples = @ExampleObject(value = """
                                    [
                                      {
                                        "notificationId": 1,
                                        "recipientId": 101,
                                        "type": "PAYMENT_CONFIRMED",
                                        "channel": "EMAIL",
                                        "status": "PENDING",
                                        "requestedAt": "2026-04-24T10:00:00",
                                        "readAt": null
                                      }
                                    ]
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid filter value"),
            @ApiResponse(responseCode = "401", description = "Missing, malformed, invalid, or expired JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user cannot access the notifications")
    })
    public ResponseEntity<List<NotificationListItemResponse>> getUserNotifications(
            @Parameter(hidden = true)
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Parameter(description = "Read filter: ALL, READ, or UNREAD")
            @RequestParam(defaultValue = "ALL") NotificationReadFilter filter
    ) {
        JwtClaims claims = jwtAuthorizationExtractor.extract(authorizationHeader);

        return ResponseEntity.ok(notificationService.getUserNotifications(claims.userId(), claims.userId(), filter));
    }
}
