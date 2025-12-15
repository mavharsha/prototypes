package com.mavharsha.jsonplaceholder;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import reactor.core.publisher.Mono;

import java.util.List;

@Client("https://jsonplaceholder.typicode.com")
public interface JsonPlaceholderClient {

    @Get("/users")
    Mono<List<User>> listUsers();
}
