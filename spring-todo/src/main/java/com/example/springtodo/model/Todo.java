package com.example.springtodo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "todos")
public class Todo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "todo_id")
    private Long todoId;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "todo_task", nullable = false)
    private String todoTask;
    
    @Column(name = "is_completed")
    private Boolean isCompleted;

    public Todo() {
    }

    public Todo(Long todoId, Long userId, String todoTask, Boolean isCompleted) {
        this.todoId = todoId;
        this.userId = userId;
        this.todoTask = todoTask;
        this.isCompleted = isCompleted;
    }

    public Long getTodoId() {
        return todoId;
    }

    public void setTodoId(Long todoId) {
        this.todoId = todoId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTodoTask() {
        return todoTask;
    }

    public void setTodoTask(String todoTask) {
        this.todoTask = todoTask;
    }

    public Boolean getIsCompleted() {
        return isCompleted;
    }

    public void setIsCompleted(Boolean isCompleted) {
        this.isCompleted = isCompleted;
    }
}

