package com.mavharsha.jsonplaceholder;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Address(
        String street,
        String suite,
        String city,
        String zipcode,
        Geo geo
) {
}
