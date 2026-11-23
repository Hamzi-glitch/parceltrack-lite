package com.parceltrack.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParcelAssignmentRepository extends JpaRepository<ParcelAssignment, Long> {

    /** The current assignment of a parcel = its newest assignment row. */
    Optional<ParcelAssignment> findFirstByParcelIdOrderByIdDesc(Long parcelId);

    /** Parcels whose CURRENT assignment belongs to this rider (older assignments don't count). */
    @Query("""
            select a from ParcelAssignment a
            where a.riderId = :riderId
              and a.id = (select max(b.id) from ParcelAssignment b where b.parcelId = a.parcelId)
            order by a.assignedAt desc
            """)
    List<ParcelAssignment> findCurrentByRiderId(@Param("riderId") Long riderId);

    /** Current assignment for each of the given parcels. */
    @Query("""
            select a from ParcelAssignment a
            where a.parcelId in :parcelIds
              and a.id = (select max(b.id) from ParcelAssignment b where b.parcelId = a.parcelId)
            """)
    List<ParcelAssignment> findCurrentByParcelIds(@Param("parcelIds") Collection<Long> parcelIds);
}
