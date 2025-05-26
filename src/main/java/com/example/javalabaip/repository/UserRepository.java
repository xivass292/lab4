package com.example.javalabaip.repository;

import com.example.javalabaip.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"locations"})
    Optional<User> findByIdWithLocations(Long id);

    Optional<User> findByUsername(String username);
}