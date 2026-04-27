package com.example.notification.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.notification.common.exception.BusinessException;
import com.example.notification.common.exception.ErrorCode;
import com.example.notification.user.dto.UserLoginRequest;
import com.example.notification.user.dto.UserLoginResponse;
import com.example.notification.user.dto.UserResponse;
import com.example.notification.user.dto.UserSignupRequest;
import com.example.notification.user.entity.User;
import com.example.notification.user.repository.UserRepository;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private FakeJwtTokenProvider jwtTokenProvider;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtTokenProvider = new FakeJwtTokenProvider();
        userService = new UserService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    @DisplayName("?뚯썝媛?낆뿉 ?깃났?섎㈃ 鍮꾨?踰덊샇瑜??댁떆濡???ν븯怨??ъ슜???묐떟??諛섑솚?쒕떎")
    void signupCreatesUserWithHashedPassword() {
        UserSignupRequest request = new UserSignupRequest("user@test.com", "tester", "password123");
        when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.signup(request);

        assertThat(response.email()).isEqualTo("user@test.com");
        assertThat(response.name()).isEqualTo("tester");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("?대? 媛?낅맂 ?대찓?쇱씠硫??뚯썝媛?낆뿉 ?ㅽ뙣?쒕떎")
    void signupFailsWhenEmailAlreadyExists() {
        UserSignupRequest request = new UserSignupRequest("user@test.com", "tester", "password123");
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("濡쒓렇?몄뿉 ?깃났?섎㈃ JWT ?좏겙??諛섑솚?쒕떎")
    void loginReturnsJwtToken() {
        User user = User.create("user@test.com", "tester", passwordEncoder.encode("password123"));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        UserLoginResponse response = userService.login(new UserLoginRequest("user@test.com", "password123"));

        assertThat(response.user().email()).isEqualTo("user@test.com");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("test-access-token");
        assertThat(response.expiresIn()).isEqualTo(3600);
    }

    @Test
    @DisplayName("議댁옱?섏? ?딅뒗 ?대찓?쇱씠硫?濡쒓렇?몄뿉 ?ㅽ뙣?쒕떎")
    void loginFailsWhenUserDoesNotExist() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(new UserLoginRequest("missing@test.com", "password123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("鍮꾨?踰덊샇媛 ?由щ㈃ 濡쒓렇?몄뿉 ?ㅽ뙣?쒕떎")
    void loginFailsWhenPasswordDoesNotMatch() {
        User user = User.create("user@test.com", "tester", passwordEncoder.encode("password123"));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.login(new UserLoginRequest("user@test.com", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    private static class FakeJwtTokenProvider extends JwtTokenProvider {

        FakeJwtTokenProvider() {
            super(java.time.Clock.systemUTC(), "test-secret", 3600);
        }

        @Override
        public String createAccessToken(User user) {
            return "test-access-token";
        }
    }
}
