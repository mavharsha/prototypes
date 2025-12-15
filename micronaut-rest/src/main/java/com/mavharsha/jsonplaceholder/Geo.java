package com.mavharsha.jsonplaceholder;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Geo(String lat, String lng) {
}
