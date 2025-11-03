package com.example.springtodo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
// GET localhost:8080/api/health
// RESPONSE: OK
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}

