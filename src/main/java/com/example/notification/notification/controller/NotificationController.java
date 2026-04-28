package com.example.notification.notification.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.common.jwt.JwtAuthorizationExtractor;
import com.example.notification.common.jwt.JwtClaims;
import com.example.notification.notification.dto.NotificationCreateRequest;
import com.example.notification.notification.dto.NotificationCreateResponse;
import com.example.notification.notification.dto.NotificationStatusResponse;
import com.example.notification.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Notification APIs")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;
    private final JwtAuthorizationExtractor jwtAuthorizationExtractor;

    public NotificationController(
            NotificationService notificationService,
            JwtAuthorizationExtractor jwtAuthorizationExtractor
    ) {
        this.notificationService = notificationService;
        this.jwtAuthorizationExtractor = jwtAuthorizationExtractor;
    }

    @PostMapping
    @Operation(
            summary = "Create notification request",
            description = "Validates the JWT bearer token, validates the notification request, and stores a PENDING notification.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Notification created",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NotificationCreateResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "notificationId": 1,
                                      "status": "PENDING",
                                      "requestedAt": "2026-04-24T10:00:00"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request body or payload.eventId is missing"),
            @ApiResponse(responseCode = "401", description = "Missing, malformed, invalid, or expired JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user does not match recipientId"),
            @ApiResponse(responseCode = "404", description = "Recipient user or active notification template not found")
    })
    public ResponseEntity<NotificationCreateResponse> create(
            @Parameter(
                    name = "Authorization",
                    description = "JWT bearer token. Example: Bearer eyJhbGciOiJIUzI1NiJ9...",
                    in = ParameterIn.HEADER,
                    required = true
            )
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody NotificationCreateRequest request
    ) {
        JwtClaims claims = jwtAuthorizationExtractor.extract(authorizationHeader);
        if (!claims.userId().equals(request.recipientId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Authenticated user cannot create notification for recipient.");
        }

        log.info(
                "Received notification create request. recipientId={}, type={}, channel={}",
                request.recipientId(),
                request.type(),
                request.channel()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.create(request));
    }

    @GetMapping("/{notificationId}")
    @Operation(
            summary = "Get notification status",
            description = "Validates the JWT bearer token and returns notification status for the authenticated recipient.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Notification status",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NotificationStatusResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "notificationId": 1,
                                      "recipientId": 101,
                                      "type": "PAYMENT_CONFIRMED",
                                      "channel": "EMAIL",
                                      "status": "RETRY_WAITING",
                                      "retryCount": 1,
                                      "nextRetryAt": "2026-04-24T10:05:00",
                                      "lastErrorCode": "EMAIL_TEMPORARY_FAILURE",
                                      "lastErrorMessage": "mock smtp timeout"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Missing, malformed, invalid, or expired JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user cannot access the notification"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<NotificationStatusResponse> getStatus(
            @Parameter(
                    name = "Authorization",
                    description = "JWT bearer token. Example: Bearer eyJhbGciOiJIUzI1NiJ9...",
                    in = ParameterIn.HEADER,
                    required = true
            )
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long notificationId
    ) {
        JwtClaims claims = jwtAuthorizationExtractor.extract(authorizationHeader);

        return ResponseEntity.ok(notificationService.getStatus(notificationId, claims.userId()));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(
            summary = "Mark notification as read",
            description = "Validates the JWT bearer token and marks the authenticated recipient's notification as read.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notification marked as read"),
            @ApiResponse(responseCode = "401", description = "Missing, malformed, invalid, or expired JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user cannot access the notification"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<Void> markAsRead(
            @Parameter(
                    name = "Authorization",
                    description = "JWT bearer token. Example: Bearer eyJhbGciOiJIUzI1NiJ9...",
                    in = ParameterIn.HEADER,
                    required = true
            )
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long notificationId
    ) {
        JwtClaims claims = jwtAuthorizationExtractor.extract(authorizationHeader);
        notificationService.markAsRead(notificationId, claims.userId());

        return ResponseEntity.noContent().build();
    }

}
