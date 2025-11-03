package com.example.springtodo.service;

import com.example.springtodo.model.Todo;
import com.example.springtodo.repository.TodoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TodoService {

    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    public List<Todo> getAllTodos() {
        return todoRepository.findAll();
    }

    public Optional<Todo> getTodoById(Long id) {
        return todoRepository.findById(id);
    }

    public List<Todo> getTodosByUserId(Long userId) {
        return todoRepository.findByUserId(userId);
    }

    public Todo createTodo(Todo todo) {
        if (todo.getIsCompleted() == null) {
            todo.setIsCompleted(false);
        }
        return todoRepository.save(todo);
    }

    public Optional<Todo> updateTodo(Long id, Todo todo) {
        if (!todoRepository.existsById(id)) {
            return Optional.empty();
        }
        todo.setTodoId(id);
        return Optional.of(todoRepository.save(todo));
    }

    public Optional<Todo> toggleComplete(Long id, boolean completed) {
        return todoRepository.findById(id)
                .map(todo -> {
                    todo.setIsCompleted(completed);
                    return todoRepository.save(todo);
                });
    }

    public boolean deleteTodo(Long id) {
        if (!todoRepository.existsById(id)) {
            return false;
        }
        todoRepository.deleteById(id);
        return true;
    }
}

