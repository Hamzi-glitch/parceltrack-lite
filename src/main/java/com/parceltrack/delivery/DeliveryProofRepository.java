package com.parceltrack.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, Long> {

    Optional<DeliveryProof> findByParcelId(Long parcelId);

    boolean existsByParcelId(Long parcelId);
}
