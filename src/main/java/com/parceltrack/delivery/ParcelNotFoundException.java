package com.parceltrack.delivery;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Generic 404. Used both when a parcel does not exist and when it exists but the current
 * user may not see it, so an attacker cannot tell the two apart.
 */
public class ParcelNotFoundException extends ResponseStatusException {

    public ParcelNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
