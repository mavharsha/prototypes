package com.example.ecommerce.api;

import io.micronaut.runtime.Micronaut;

/**
 * Main application entry point.
 * This is in the API module since it's the runnable layer.
 */
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
