package com.example.ecommerce.api.handler;

import com.example.ecommerce.common.exception.CartEmptyException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/**
 * Exception handler for CartEmptyException.
 */
@Singleton
@Produces
public class CartEmptyExceptionHandler
        implements ExceptionHandler<CartEmptyException, HttpResponse<ErrorResponse>> {

    @Override
    public HttpResponse<ErrorResponse> handle(HttpRequest request, CartEmptyException exception) {
        return HttpResponse.badRequest(
                new ErrorResponse("CART_EMPTY", exception.getMessage())
        );
    }
}
