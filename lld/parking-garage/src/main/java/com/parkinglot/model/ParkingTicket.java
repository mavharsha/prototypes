package com.parkinglot.model;

import com.parkinglot.exception.InvalidTicketException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class ParkingTicket {

    private static final AtomicLong ID_GEN = new AtomicLong(1);

    private final long ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final Instant entryTime;
    private Instant exitTime;
    private BigDecimal fee;
    private TicketStatus status;

    private ParkingTicket(Vehicle vehicle, ParkingSpot spot, Instant entryTime) {
        this.ticketId = ID_GEN.getAndIncrement();
        this.vehicle = vehicle;
        this.spot = spot;
        this.entryTime = entryTime;
        this.exitTime = null;
        this.fee = null;
        this.status = TicketStatus.ACTIVE;
    }

    // ── Factory ─────────────────────────────────────────────────

    public static ParkingTicket issue(Vehicle vehicle, ParkingSpot spot) {
        return new ParkingTicket(vehicle, spot, Instant.now());
    }

    /** Factory with explicit entry time — useful for demo scenarios. */
    public static ParkingTicket issue(Vehicle vehicle, ParkingSpot spot, Instant entryTime) {
        return new ParkingTicket(vehicle, spot, entryTime);
    }

    // ── Lifecycle ───────────────────────────────────────────────

    public void markPaid(BigDecimal fee) {
        if (status == TicketStatus.PAID) {
            throw new InvalidTicketException("Ticket " + ticketId + " is already paid");
        }
        this.exitTime = Instant.now();
        this.fee = fee;
        this.status = TicketStatus.PAID;
    }

    /** Mark paid with an explicit exit time — useful for demo scenarios. */
    public void markPaid(BigDecimal fee, Instant exitTime) {
        if (status == TicketStatus.PAID) {
            throw new InvalidTicketException("Ticket " + ticketId + " is already paid");
        }
        this.exitTime = exitTime;
        this.fee = fee;
        this.status = TicketStatus.PAID;
    }

    // ── Queries ─────────────────────────────────────────────────

    public Duration getDuration() {
        Instant end = (exitTime != null) ? exitTime : Instant.now();
        return Duration.between(entryTime, end);
    }

    public boolean isActive() {
        return status == TicketStatus.ACTIVE;
    }

    // ── Getters ─────────────────────────────────────────────────

    public long getTicketId()       { return ticketId; }
    public Vehicle getVehicle()     { return vehicle; }
    public ParkingSpot getSpot()    { return spot; }
    public Instant getEntryTime()   { return entryTime; }
    public Instant getExitTime()    { return exitTime; }
    public BigDecimal getFee()      { return fee; }
    public TicketStatus getStatus() { return status; }

    @Override
    public String toString() {
        return String.format("Ticket{id=%d, vehicle=%s, spot=%d, floor=%d, status=%s, fee=%s}",
                ticketId, vehicle, spot.getSpotId(), spot.getFloor(), status,
                fee != null ? "$" + fee.toPlainString() : "N/A");
    }
}
