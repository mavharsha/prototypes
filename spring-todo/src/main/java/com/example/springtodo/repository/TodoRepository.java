package com.example.springtodo.repository;

import com.example.springtodo.model.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TodoRepository extends JpaRepository<Todo, Long> {
    // JpaRepository provides all basic CRUD operations
    // Custom query method - Spring Data JPA will implement this automatically
    List<Todo> findByUserId(Long userId);
}

