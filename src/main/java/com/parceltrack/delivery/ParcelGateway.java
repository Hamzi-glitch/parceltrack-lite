package com.parceltrack.delivery;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything M3 needs from M2's parcels. M3 code only talks to parcels through this
 * interface, so when M2's Parcel entity lands, only the implementation has to change.
 */
public interface ParcelGateway {

    /** What M3 pages need about a parcel. Never the whole Parcel entity. */
    record ParcelSummary(Long id,
                         String trackingNumber,
                         String recipientName,
                         String recipientPhone,
                         String recipientAddress,
                         DeliveryStatus status) {
    }

    Optional<ParcelSummary> findById(Long parcelId);

    List<ParcelSummary> findByIds(Collection<Long> parcelIds);

    /** Parcels the Admin may still assign (everything not yet delivered), newest first. */
    List<ParcelSummary> findAssignable();

    /** Sets parcels.current_status. Must run inside the caller's transaction. */
    void updateStatus(Long parcelId, DeliveryStatus status);

    /** Number of parcels in each status, counted in the database. Every status is present. */
    Map<DeliveryStatus, Long> countByStatus();
}
