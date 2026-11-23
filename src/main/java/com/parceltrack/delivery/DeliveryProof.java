package com.parceltrack.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One proof-of-delivery photo per parcel (parcel_id is UNIQUE). Only the random
 * server-generated filename is stored; the client's original filename is discarded.
 */
@Entity
@Table(name = "delivery_proofs")
public class DeliveryProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcel_id", nullable = false, unique = true, updatable = false)
    private Long parcelId;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private Long uploadedBy;

    @Column(name = "stored_filename", nullable = false, length = 64, updatable = false)
    private String storedFilename;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    protected DeliveryProof() {
    }

    public DeliveryProof(Long parcelId, Long uploadedBy, String storedFilename) {
        this.parcelId = parcelId;
        this.uploadedBy = uploadedBy;
        this.storedFilename = storedFilename;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getParcelId() { return parcelId; }
    public Long getUploadedBy() { return uploadedBy; }
    public String getStoredFilename() { return storedFilename; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
