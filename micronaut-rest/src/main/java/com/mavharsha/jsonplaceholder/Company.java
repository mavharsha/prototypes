package com.mavharsha.jsonplaceholder;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Company(
        String name,
        String catchPhrase,
        String bs
) {
}
