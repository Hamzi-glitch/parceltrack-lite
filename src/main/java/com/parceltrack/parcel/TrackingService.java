package com.parceltrack.parcel;

import com.parceltrack.delivery.DeliveryHistory;
import com.parceltrack.delivery.DeliveryHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Public tracking. Anyone may call this without logging in, so it returns a small DTO with the
 * tracking number and the status history only. It never returns the Parcel entity, and it never
 * includes the recipient's name, phone or address, who changed a status, or free-text remarks.
 */
@Service
public class TrackingService {

    /** What the public tracking page may show. */
    public record TrackingView(String trackingNumber, String statusLabel, List<TrackingEvent> events) {
    }

    public record TrackingEvent(String statusLabel, LocalDateTime changedAt) {
    }

    private final ParcelRepository parcels;
    private final DeliveryHistoryRepository history;

    public TrackingService(ParcelRepository parcels, DeliveryHistoryRepository history) {
        this.parcels = parcels;
        this.history = history;
    }

    /**
     * Looks a parcel up by what the visitor typed. Anything that is not a well-formed tracking
     * number is rejected without touching the database. An empty result means "not found"
     * for every reason, so the page cannot be used to learn which numbers exist.
     */
    @Transactional(readOnly = true)
    public Optional<TrackingView> find(String rawNumber) {
        String number = TrackingNumberGenerator.normalize(rawNumber);
        if (number == null) {
            return Optional.empty();
        }
        return parcels.findByTrackingNumber(number).map(parcel -> {
            List<TrackingEvent> events = history.findByParcelIdOrderByChangedAtAscIdAsc(parcel.getId()).stream()
                    .map(TrackingService::toEvent)
                    .toList();
            if (events.isEmpty() && parcel.getCreatedAt() != null) {
                // Parcels created without a history row still show where they stand.
                events = List.of(new TrackingEvent(parcel.getCurrentStatus().getLabel(), parcel.getCreatedAt()));
            }
            return new TrackingView(parcel.getTrackingNumber(), parcel.getCurrentStatus().getLabel(), events);
        });
    }

    private static TrackingEvent toEvent(DeliveryHistory row) {
        return new TrackingEvent(row.getStatus().getLabel(), row.getChangedAt());
    }
}
