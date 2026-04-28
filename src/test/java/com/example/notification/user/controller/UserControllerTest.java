package com.example.notification.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.notification.user.dto.UserLoginRequest;
import com.example.notification.user.dto.UserLoginResponse;
import com.example.notification.user.dto.UserResponse;
import com.example.notification.user.dto.UserSignupRequest;
import com.example.notification.user.entity.UserStatus;
import com.example.notification.user.service.UserService;

class UserControllerTest {

    @Test
    @DisplayName("회원가입 API는 CREATED 상태와 사용자 응답을 반환한다")
    void signupReturnsCreatedResponse() {
        UserService userService = org.mockito.Mockito.mock(UserService.class);
        UserController controller = new UserController(userService);
        UserSignupRequest request = new UserSignupRequest("user@test.com", "tester", "password123");
        UserResponse serviceResponse = new UserResponse(1L, "user@test.com", "tester", UserStatus.ACTIVE);
        when(userService.signup(request)).thenReturn(serviceResponse);

        ResponseEntity<UserResponse> response = controller.signup(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
        verify(userService).signup(request);
    }

    @Test
    @DisplayName("로그인 API는 OK 상태와 JWT 응답을 반환한다")
    void loginReturnsOkResponse() {
        UserService userService = org.mockito.Mockito.mock(UserService.class);
        UserController controller = new UserController(userService);
        UserLoginRequest request = new UserLoginRequest("user@test.com", "password123");
        UserResponse userResponse = new UserResponse(1L, "user@test.com", "tester", UserStatus.ACTIVE);
        UserLoginResponse serviceResponse = UserLoginResponse.bearer(userResponse, "access-token", 3600);
        when(userService.login(request)).thenReturn(serviceResponse);

        ResponseEntity<UserLoginResponse> response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
        verify(userService).login(request);
    }
}
