package com.parceltrack.parcel;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Generic 404. Used both when a parcel does not exist and when it belongs to another Seller,
 * so nobody can tell the two cases apart by changing an ID in the URL.
 */
public class SellerParcelNotFoundException extends ResponseStatusException {

    public SellerParcelNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
