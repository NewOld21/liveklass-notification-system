package com.example.notification.user.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.notification.user.dto.UserLoginRequest;
import com.example.notification.user.dto.UserLoginResponse;
import com.example.notification.user.dto.UserResponse;
import com.example.notification.user.dto.UserSignupRequest;
import com.example.notification.user.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody UserSignupRequest request) {
        log.info("Received signup request. email={}", request.email());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        log.info("Received login request. email={}", request.email());

        return ResponseEntity.ok(userService.login(request));
    }
}
