package com.example.ecommerce.service.impl;

import com.example.ecommerce.common.exception.InsufficientStockException;
import com.example.ecommerce.common.exception.NotFoundException;
import com.example.ecommerce.domain.entity.Product;
import com.example.ecommerce.domain.entity.Reservation;
import com.example.ecommerce.domain.entity.ReservationStatus;
import com.example.ecommerce.domain.repository.ProductRepository;
import com.example.ecommerce.domain.repository.ReservationRepository;
import com.example.ecommerce.service.ReservationService;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Implementation of ReservationService.
 * Manages stock reservations with expiry.
 */
@Singleton
public class ReservationServiceImpl implements ReservationService {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationServiceImpl.class);
    private static final long RESERVATION_EXPIRY_MINUTES = 15;

    private final ReservationRepository reservationRepository;
    private final ProductRepository productRepository;

    public ReservationServiceImpl(ReservationRepository reservationRepository, ProductRepository productRepository) {
        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
    }

    @Override
    public Reservation reserveStock(String customerId, String productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));

        if (!product.hasStock(quantity)) {
            throw new InsufficientStockException(productId, quantity, product.getStock());
        }

        product.reduceStock(quantity);
        productRepository.save(product);

        Reservation reservation = new Reservation(customerId, productId, quantity, RESERVATION_EXPIRY_MINUTES);
        reservationRepository.save(reservation);

        LOG.info("Reserved {} units of product {} for customer {}, expires at {}",
                quantity, productId, customerId, reservation.getExpiresAt());

        return reservation;
    }

    @Override
    public void cancelReservation(String customerId, String productId) {
        List<Reservation> reservations = reservationRepository
                .findByCustomerIdAndProductIdAndStatus(customerId, productId, ReservationStatus.ACTIVE);

        for (Reservation reservation : reservations) {
            productRepository.findById(reservation.getProductId()).ifPresent(product -> {
                product.addStock(reservation.getQuantity());
                productRepository.save(product);
            });
            reservation.cancel();
            reservationRepository.save(reservation);

            LOG.info("Cancelled reservation {} for product {}, restored {} units",
                    reservation.getId(), productId, reservation.getQuantity());
        }
    }

    @Override
    public void cancelAllReservations(String customerId) {
        List<Reservation> reservations = reservationRepository
                .findByCustomerIdAndStatus(customerId, ReservationStatus.ACTIVE);

        for (Reservation reservation : reservations) {
            productRepository.findById(reservation.getProductId()).ifPresent(product -> {
                product.addStock(reservation.getQuantity());
                productRepository.save(product);
            });
            reservation.cancel();
            reservationRepository.save(reservation);
        }

        LOG.info("Cancelled all {} active reservations for customer {}", reservations.size(), customerId);
    }

    @Override
    public void confirmReservations(String customerId) {
        List<Reservation> reservations = reservationRepository
                .findByCustomerIdAndStatus(customerId, ReservationStatus.ACTIVE);

        for (Reservation reservation : reservations) {
            reservation.confirm();
            reservationRepository.save(reservation);
        }

        LOG.info("Confirmed {} reservations for customer {}", reservations.size(), customerId);
    }

    @Override
    public void expireReservations() {
        List<Reservation> activeReservations = reservationRepository.findByStatus(ReservationStatus.ACTIVE);

        int expiredCount = 0;
        for (Reservation reservation : activeReservations) {
            if (reservation.isExpired()) {
                productRepository.findById(reservation.getProductId()).ifPresent(product -> {
                    product.addStock(reservation.getQuantity());
                    productRepository.save(product);
                });
                reservation.expire();
                reservationRepository.save(reservation);
                expiredCount++;

                LOG.info("Expired reservation {} for product {}, restored {} units",
                        reservation.getId(), reservation.getProductId(), reservation.getQuantity());
            }
        }

        if (expiredCount > 0) {
            LOG.info("Expired {} reservations and restored stock", expiredCount);
        }
    }

    @Override
    public List<Reservation> getActiveReservations(String customerId) {
        return reservationRepository.findByCustomerIdAndStatus(customerId, ReservationStatus.ACTIVE);
    }
}
