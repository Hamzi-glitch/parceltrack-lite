package com.parceltrack.delivery;

/**
 * A request broke a delivery rule (bad status, wrong transition, invalid upload, ...).
 * The message is safe to show to the user: it never contains internal details.
 */
public class DeliveryRuleException extends RuntimeException {

    public DeliveryRuleException(String userMessage) {
        super(userMessage);
    }
}
