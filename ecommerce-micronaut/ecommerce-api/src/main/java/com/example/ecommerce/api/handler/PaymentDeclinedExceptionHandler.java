package com.example.ecommerce.api.handler;

import com.example.ecommerce.common.exception.PaymentDeclinedException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/**
 * Exception handler for PaymentDeclinedException.
 */
@Singleton
@Produces
public class PaymentDeclinedExceptionHandler
        implements ExceptionHandler<PaymentDeclinedException, HttpResponse<ErrorResponse>> {

    @Override
    public HttpResponse<ErrorResponse> handle(HttpRequest request, PaymentDeclinedException exception) {
        return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                new ErrorResponse("PAYMENT_DECLINED", exception.getMessage())
        );
    }
}
