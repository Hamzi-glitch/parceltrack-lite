package com.parceltrack.parcel;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * What the Seller types into the registration form. All rules are enforced on the server
 * (browser checks are only a convenience). Change the phone pattern here if the group agrees
 * on a different format.
 */
public class ParcelForm {

    /** No control characters (newlines, tabs, ...): keeps logs and pages clean. */
    private static final String NO_CONTROL_CHARS = "^[^\\p{Cntrl}]*$";

    @NotBlank(message = "Recipient name is required.")
    @Size(max = Parcel.MAX_NAME, message = "Recipient name must be at most 100 characters.")
    @Pattern(regexp = NO_CONTROL_CHARS, message = "Recipient name contains invalid characters.")
    private String recipientName = "";

    /** Digits only, with an optional leading +, 9 to 15 digits (for example 0123456789 or +60123456789). */
    @NotBlank(message = "Phone number is required.")
    @Pattern(regexp = "^\\+?[0-9]{9,15}$",
            message = "Phone number must be 9 to 15 digits, with an optional leading +.")
    private String recipientPhone = "";

    @NotBlank(message = "Address is required.")
    @Size(max = Parcel.MAX_ADDRESS, message = "Address must be at most 255 characters.")
    @Pattern(regexp = NO_CONTROL_CHARS, message = "Address contains invalid characters.")
    private String recipientAddress = "";

    @NotNull(message = "Weight is required.")
    @DecimalMin(value = "0.01", message = "Weight must be more than 0 kg.")
    @DecimalMax(value = "999.99", message = "Weight must be at most 999.99 kg.")
    @Digits(integer = 3, fraction = 2, message = "Weight can have at most 2 decimal places.")
    private BigDecimal weight;

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }
    public String getRecipientAddress() { return recipientAddress; }
    public void setRecipientAddress(String recipientAddress) { this.recipientAddress = recipientAddress; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
}
