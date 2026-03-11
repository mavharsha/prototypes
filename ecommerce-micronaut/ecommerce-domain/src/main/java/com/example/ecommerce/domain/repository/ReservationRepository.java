package com.example.ecommerce.domain.repository;

import com.example.ecommerce.domain.entity.Reservation;
import com.example.ecommerce.domain.entity.ReservationStatus;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Reservation persistence.
 */
public interface ReservationRepository {

    Reservation save(Reservation reservation);

    Optional<Reservation> findById(String id);

    List<Reservation> findByCustomerId(String customerId);

    List<Reservation> findByCustomerIdAndStatus(String customerId, ReservationStatus status);

    List<Reservation> findByCustomerIdAndProductIdAndStatus(String customerId, String productId, ReservationStatus status);

    List<Reservation> findByStatus(ReservationStatus status);

    void deleteById(String id);
}
