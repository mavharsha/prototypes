package com.example.ecommerce.api.handler;

import com.example.ecommerce.common.exception.ReservationExpiredException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/**
 * Exception handler for ReservationExpiredException.
 */
@Singleton
@Produces
public class ReservationExpiredExceptionHandler
        implements ExceptionHandler<ReservationExpiredException, HttpResponse<ErrorResponse>> {

    @Override
    public HttpResponse<ErrorResponse> handle(HttpRequest request, ReservationExpiredException exception) {
        return HttpResponse.status(HttpStatus.CONFLICT).body(
                new ErrorResponse("RESERVATION_EXPIRED", exception.getMessage())
        );
    }
}
