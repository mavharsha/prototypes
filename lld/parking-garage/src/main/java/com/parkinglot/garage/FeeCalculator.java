package com.parkinglot.garage;

import com.parkinglot.model.ParkingTicket;
import com.parkinglot.model.VehicleType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Stateless fee calculator.
 *
 * Hourly rates by vehicle type, rounded up to the next hour.
 * First 15 minutes free (grace period).
 */
public class FeeCalculator {

    private static final Map<VehicleType, BigDecimal> HOURLY_RATES = new EnumMap<>(VehicleType.class);

    static {
        HOURLY_RATES.put(VehicleType.MOTORCYCLE, new BigDecimal("2.00"));
        HOURLY_RATES.put(VehicleType.CAR,        new BigDecimal("5.00"));
        HOURLY_RATES.put(VehicleType.TRUCK,      new BigDecimal("10.00"));
    }

    private static final long GRACE_PERIOD_MINUTES = 15;

    /**
     * Calculate fee for a parking session.
     *
     * @param ticket    the active parking ticket
     * @param exitTime  when the vehicle is leaving
     * @return fee in dollars, rounded up to the next hour after grace period
     */
    public BigDecimal calculate(ParkingTicket ticket, Instant exitTime) {
        Duration duration = Duration.between(ticket.getEntryTime(), exitTime);
        long totalMinutes = duration.toMinutes();

        // Grace period — free if under 15 minutes
        if (totalMinutes <= GRACE_PERIOD_MINUTES) {
            return BigDecimal.ZERO;
        }

        // Round up to next hour
        long hours = (totalMinutes + 59) / 60;

        BigDecimal rate = HOURLY_RATES.get(ticket.getVehicle().getType());
        return rate.multiply(BigDecimal.valueOf(hours)).setScale(2, RoundingMode.HALF_UP);
    }
}
