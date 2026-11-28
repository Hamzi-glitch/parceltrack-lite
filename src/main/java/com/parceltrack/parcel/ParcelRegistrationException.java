package com.parceltrack.parcel;

/** Registration failed for a reason the Seller can be told about. The message never contains internal details. */
public class ParcelRegistrationException extends RuntimeException {

    public ParcelRegistrationException(String userMessage) {
        super(userMessage);
    }
}
