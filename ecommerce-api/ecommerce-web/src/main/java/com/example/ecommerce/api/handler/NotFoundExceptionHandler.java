package com.example.ecommerce.api.handler;

import com.example.ecommerce.common.exception.NotFoundException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Singleton
@Produces
public class NotFoundExceptionHandler
        implements ExceptionHandler<NotFoundException, HttpResponse<ErrorResponse>> {

    @Override
    public HttpResponse<ErrorResponse> handle(HttpRequest request, NotFoundException exception) {
        return HttpResponse.notFound(
                new ErrorResponse("NOT_FOUND", exception.getMessage())
        );
    }
}
