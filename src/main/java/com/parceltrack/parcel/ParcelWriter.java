package com.parceltrack.parcel;

import com.parceltrack.delivery.DeliveryHistory;
import com.parceltrack.delivery.DeliveryHistoryRepository;
import com.parceltrack.delivery.DeliveryStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Saves one parcel and its first history row in ONE transaction, so a parcel can never exist
 * without its "Registered" entry (or the other way round).
 *
 * This is a separate bean on purpose: if the tracking number collides, the transaction rolls back
 * at this bean's boundary and {@link ParcelService} can safely try again with a new number in a
 * fresh transaction. Calling a @Transactional method on the same object would not do that.
 * Only ParcelService should call it, because it does no authorization of its own.
 */
@Component
public class ParcelWriter {

    private final ParcelRepository parcels;
    private final DeliveryHistoryRepository history;

    public ParcelWriter(ParcelRepository parcels, DeliveryHistoryRepository history) {
        this.parcels = parcels;
        this.history = history;
    }

    @Transactional
    public Parcel insert(Long sellerId, String trackingNumber, String name, String phone, String address,
                         BigDecimal weight) {
        // saveAndFlush so a duplicate tracking number fails here, inside the transaction.
        Parcel saved = parcels.saveAndFlush(new Parcel(sellerId, trackingNumber, name, phone, address, weight));
        history.save(new DeliveryHistory(saved.getId(), DeliveryStatus.REGISTERED, sellerId, "Parcel registered"));
        return saved;
    }
}
