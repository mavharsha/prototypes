package com.example.ecommerce.api.handler;

import com.example.ecommerce.common.exception.DuplicateResourceException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Singleton
@Produces
public class DuplicateResourceExceptionHandler
        implements ExceptionHandler<DuplicateResourceException, HttpResponse<ErrorResponse>> {

    @Override
    public HttpResponse<ErrorResponse> handle(HttpRequest request, DuplicateResourceException exception) {
        return HttpResponse.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATE_RESOURCE", exception.getMessage()));
    }
}
