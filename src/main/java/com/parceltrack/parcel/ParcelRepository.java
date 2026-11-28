package com.parceltrack.parcel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Every Seller query includes seller_id, so a Seller can never load another Seller's parcel
 * through these methods. All values are bound as parameters; nothing is concatenated into a query.
 */
public interface ParcelRepository extends JpaRepository<Parcel, Long> {

    Optional<Parcel> findByIdAndSellerId(Long id, Long sellerId);

    List<Parcel> findBySellerIdOrderByCreatedAtDescIdDesc(Long sellerId);

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumber(String trackingNumber);

    /**
     * Searches one Seller's parcels by tracking number or recipient name. The caller passes a
     * lower-case LIKE pattern in which % _ and ! from the user were escaped with the escape
     * character !, so user input is matched literally.
     */
    @Query("""
            select p from Parcel p
            where p.sellerId = :sellerId
              and (lower(p.trackingNumber) like :pattern escape '!'
                   or lower(p.recipientName) like :pattern escape '!')
            order by p.createdAt desc, p.id desc
            """)
    List<Parcel> searchOwn(@Param("sellerId") Long sellerId, @Param("pattern") String pattern);
}
