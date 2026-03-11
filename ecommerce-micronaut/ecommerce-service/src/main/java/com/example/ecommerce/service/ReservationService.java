package com.example.ecommerce.service;

import com.example.ecommerce.domain.entity.Reservation;

import java.util.List;

/**
 * Service interface for stock reservation operations.
 */
public interface ReservationService {

    Reservation reserveStock(String customerId, String productId, int quantity);

    void cancelReservation(String customerId, String productId);

    void cancelAllReservations(String customerId);

    void confirmReservations(String customerId);

    void expireReservations();

    List<Reservation> getActiveReservations(String customerId);
}
