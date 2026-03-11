package com.example.ecommerce.api.handler;

import com.example.ecommerce.common.exception.InsufficientStockException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Singleton
@Produces
public class InsufficientStockExceptionHandler
        implements ExceptionHandler<InsufficientStockException, HttpResponse<ErrorResponse>> {

    @Override
    public HttpResponse<ErrorResponse> handle(HttpRequest request, InsufficientStockException exception) {
        return HttpResponse.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INSUFFICIENT_STOCK", exception.getMessage()));
    }
}
