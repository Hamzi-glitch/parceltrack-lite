package com.parceltrack.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryHistoryRepository extends JpaRepository<DeliveryHistory, Long> {

    /** Oldest first, for timelines (also usable by M2's public tracking page). */
    List<DeliveryHistory> findByParcelIdOrderByChangedAtAscIdAsc(Long parcelId);
}
