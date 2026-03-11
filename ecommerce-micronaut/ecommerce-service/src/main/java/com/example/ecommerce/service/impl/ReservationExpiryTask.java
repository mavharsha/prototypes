package com.example.ecommerce.service.impl;

import com.example.ecommerce.service.ReservationService;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled task that expires stale stock reservations.
 * Runs every minute to restore stock for expired reservations.
 */
@Singleton
public class ReservationExpiryTask {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationExpiryTask.class);

    private final ReservationService reservationService;

    public ReservationExpiryTask(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelay = "1m")
    public void cleanupExpiredReservations() {
        LOG.debug("Running reservation expiry cleanup");
        reservationService.expireReservations();
    }
}
