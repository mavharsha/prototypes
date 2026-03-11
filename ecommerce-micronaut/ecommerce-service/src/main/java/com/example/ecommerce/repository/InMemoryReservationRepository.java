package com.example.ecommerce.repository;

import com.example.ecommerce.domain.entity.Reservation;
import com.example.ecommerce.domain.entity.ReservationStatus;
import com.example.ecommerce.domain.repository.ReservationRepository;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ReservationRepository.
 */
@Singleton
public class InMemoryReservationRepository implements ReservationRepository {

    private final Map<String, Reservation> store = new ConcurrentHashMap<>();

    @Override
    public Reservation save(Reservation reservation) {
        store.put(reservation.getId(), reservation);
        return reservation;
    }

    @Override
    public Optional<Reservation> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Reservation> findByCustomerId(String customerId) {
        return store.values().stream()
                .filter(r -> r.getCustomerId().equals(customerId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Reservation> findByCustomerIdAndStatus(String customerId, ReservationStatus status) {
        return store.values().stream()
                .filter(r -> r.getCustomerId().equals(customerId) && r.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<Reservation> findByCustomerIdAndProductIdAndStatus(String customerId, String productId, ReservationStatus status) {
        return store.values().stream()
                .filter(r -> r.getCustomerId().equals(customerId)
                        && r.getProductId().equals(productId)
                        && r.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<Reservation> findByStatus(ReservationStatus status) {
        return store.values().stream()
                .filter(r -> r.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }
}
