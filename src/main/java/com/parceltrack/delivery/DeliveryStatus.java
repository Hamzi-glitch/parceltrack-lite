package com.parceltrack.delivery;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The only parcel statuses the system accepts. Stored in the database as the enum name
 * (e.g. OUT_FOR_DELIVERY). Anything else submitted by a client is rejected.
 */
public enum DeliveryStatus {
    REGISTERED("Registered"),
    ASSIGNED("Assigned"),
    OUT_FOR_DELIVERY("Out for Delivery"),
    DELIVERED("Delivered"),
    FAILED("Failed");

    private final String label;

    DeliveryStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Strict parse: exact enum name only, no trimming tricks, no case folding. */
    public static Optional<DeliveryStatus> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (DeliveryStatus status : values()) {
            if (status.name().equals(value)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }

    /** Statuses a Rider may move a parcel to from this status. */
    public Set<DeliveryStatus> riderTransitions() {
        return switch (this) {
            case ASSIGNED -> EnumSet.of(OUT_FOR_DELIVERY);
            case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED, FAILED);
            case FAILED -> EnumSet.of(OUT_FOR_DELIVERY);
            case REGISTERED, DELIVERED -> EnumSet.noneOf(DeliveryStatus.class);
        };
    }

    public boolean canRiderMoveTo(DeliveryStatus next) {
        return riderTransitions().contains(next);
    }

    /** A parcel can be (re)assigned by the Admin until it has been delivered. */
    public boolean isAssignable() {
        return this != DELIVERED;
    }
}
