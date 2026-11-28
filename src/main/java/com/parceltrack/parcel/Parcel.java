package com.parceltrack.parcel;

import com.parceltrack.delivery.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A registered parcel. Holds personal data about the recipient, so this entity is never sent to
 * a page that the public can open (public tracking uses {@link TrackingService.TrackingView}).
 * There is deliberately no toString(): recipient data must not end up in logs.
 */
@Entity
@Table(name = "parcels")
public class Parcel {

    public static final int MAX_NAME = 100;
    public static final int MAX_PHONE = 20;
    public static final int MAX_ADDRESS = 255;
    public static final int MAX_TRACKING_NUMBER = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;

    @Column(name = "tracking_number", nullable = false, unique = true, updatable = false,
            length = MAX_TRACKING_NUMBER)
    private String trackingNumber;

    @Column(name = "recipient_name", nullable = false, length = MAX_NAME)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = MAX_PHONE)
    private String recipientPhone;

    @Column(name = "recipient_address", nullable = false, length = MAX_ADDRESS)
    private String recipientAddress;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal weight;

    // Stored as VARCHAR (not a MySQL ENUM) so Hibernate's schema validation matches Flyway.
    // M3 updates this column through ParcelGateway when a Rider changes the status.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "current_status", nullable = false, length = 30)
    private DeliveryStatus currentStatus;

    // The database fills this in for rows inserted by plain SQL, so it is not marked NOT NULL here.
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    protected Parcel() {
    }

    public Parcel(Long sellerId, String trackingNumber, String recipientName, String recipientPhone,
                  String recipientAddress, BigDecimal weight) {
        this.sellerId = sellerId;
        this.trackingNumber = trackingNumber;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.recipientAddress = recipientAddress;
        this.weight = weight;
        this.currentStatus = DeliveryStatus.REGISTERED;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getRecipientAddress() { return recipientAddress; }
    public BigDecimal getWeight() { return weight; }
    public DeliveryStatus getCurrentStatus() { return currentStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
