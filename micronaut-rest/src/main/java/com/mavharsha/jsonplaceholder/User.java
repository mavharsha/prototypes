package com.mavharsha.jsonplaceholder;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record User(
        Integer id,
        String name,
        String username,
        String email,
        Address address,
        String phone,
        String website,
        Company company
) {
}
