package com.example.notification.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.notification.user.entity.User;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("?대찓?쇰줈 ?ъ슜?먮? 議고쉶?????덈떎")
    void findByEmailReturnsUser() {
        User user = userRepository.save(User.create("find-user@test.com", "tester", "password-hash"));

        assertThat(userRepository.findByEmail("find-user@test.com"))
                .isPresent()
                .get()
                .extracting(User::getId, User::getEmail, User::getName)
                .containsExactly(user.getId(), "find-user@test.com", "tester");
    }

    @Test
    @DisplayName("議댁옱?섏? ?딅뒗 ?대찓?쇱씠硫?鍮?Optional??諛섑솚?쒕떎")
    void findByEmailReturnsEmptyWhenEmailDoesNotExist() {
        assertThat(userRepository.findByEmail("missing@test.com")).isEmpty();
    }

    @Test
    @DisplayName("?대찓?쇱씠 議댁옱?섎뒗吏 ?뺤씤?????덈떎")
    void existsByEmailReturnsTrue() {
        userRepository.save(User.create("exists-user@test.com", "tester", "password-hash"));

        assertThat(userRepository.existsByEmail("exists-user@test.com")).isTrue();
        assertThat(userRepository.existsByEmail("missing@test.com")).isFalse();
    }
}
