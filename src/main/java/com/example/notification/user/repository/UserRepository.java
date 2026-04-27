package com.example.notification.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.notification.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
