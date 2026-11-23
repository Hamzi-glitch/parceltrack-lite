package com.parceltrack.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Append-only record of every status change: which status, who changed it and when.
 * Rows are never updated or deleted, so earlier statuses are preserved.
 */
@Entity
@Table(name = "delivery_history")
public class DeliveryHistory {

    public static final int MAX_REMARKS = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private Long parcelId;

    // Stored as VARCHAR (not a MySQL ENUM) so Hibernate's schema validation matches Flyway.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30, updatable = false)
    private DeliveryStatus status;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private Long changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @Column(length = MAX_REMARKS, updatable = false)
    private String remarks;

    protected DeliveryHistory() {
    }

    public DeliveryHistory(Long parcelId, DeliveryStatus status, Long changedBy, String remarks) {
        this.parcelId = parcelId;
        this.status = status;
        this.changedBy = changedBy;
        this.remarks = remarks;
        this.changedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getParcelId() { return parcelId; }
    public DeliveryStatus getStatus() { return status; }
    public Long getChangedBy() { return changedBy; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public String getRemarks() { return remarks; }
}
