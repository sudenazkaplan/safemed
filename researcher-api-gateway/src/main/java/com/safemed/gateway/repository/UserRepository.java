package com.safemed.gateway.repository;

import com.safemed.gateway.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // used for login + unique username check
    Optional<User> findByUsername(String username);
}
