package com.mavharsha;

import com.mavharsha.jsonplaceholder.JsonPlaceholderClient;
import com.mavharsha.jsonplaceholder.User;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

import java.util.List;

@Controller("/users")
public class UsersController {

    private final JsonPlaceholderClient client;

    @Inject
    public UsersController(JsonPlaceholderClient client) {
        this.client = client;
    }

    @Get
    public Mono<List<User>> listUsers() {
        return client.listUsers();
    }
}
