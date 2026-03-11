package com.example.ecommerce.api.handler;

import com.example.ecommerce.common.exception.InvalidOperationException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Singleton
@Produces
public class InvalidOperationExceptionHandler
        implements ExceptionHandler<InvalidOperationException, HttpResponse<ErrorResponse>> {

    @Override
    public HttpResponse<ErrorResponse> handle(HttpRequest request, InvalidOperationException exception) {
        return HttpResponse.badRequest(
                new ErrorResponse("INVALID_OPERATION", exception.getMessage())
        );
    }
}
