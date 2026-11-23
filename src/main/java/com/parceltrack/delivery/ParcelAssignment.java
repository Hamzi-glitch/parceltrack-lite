package com.parceltrack.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One row per assignment. Reassigning a parcel adds a new row, so the history of who was
 * assigned is kept. The parcel's CURRENT assignment is the row with the highest id.
 */
@Entity
@Table(name = "parcel_assignments")
public class ParcelAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcel_id", nullable = false)
    private Long parcelId;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    protected ParcelAssignment() {
    }

    public ParcelAssignment(Long parcelId, Long riderId, Long assignedBy) {
        this.parcelId = parcelId;
        this.riderId = riderId;
        this.assignedBy = assignedBy;
        this.assignedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getParcelId() { return parcelId; }
    public Long getRiderId() { return riderId; }
    public Long getAssignedBy() { return assignedBy; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
}
