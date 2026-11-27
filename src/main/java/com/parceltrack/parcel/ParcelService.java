package com.parceltrack.parcel;

import com.parceltrack.delivery.DeliveryHistory;
import com.parceltrack.delivery.DeliveryHistoryRepository;
import com.parceltrack.user.Role;
import com.parceltrack.user.User;
import com.parceltrack.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Seller-side parcel rules. The URL rules in SecurityConfig and @PreAuthorize are the first
 * check; every method here re-checks the Seller's role and, for single parcels, ownership, so
 * authorization never depends on the page alone.
 */
@Service
public class ParcelService {

    private static final Logger log = LoggerFactory.getLogger(ParcelService.class);

    static final int MAX_SEARCH_LENGTH = 100;
    private static final int MAX_TRACKING_NUMBER_ATTEMPTS = 5;
    private static final char LIKE_ESCAPE = '!';

    /** A parcel with its status history, for the Seller's detail page. */
    public record ParcelDetail(Parcel parcel, List<HistoryEntry> history) {
    }

    public record HistoryEntry(String statusLabel, LocalDateTime changedAt) {
    }

    private final ParcelRepository parcels;
    private final ParcelWriter writer;
    private final DeliveryHistoryRepository history;
    private final UserRepository users;
    private final TrackingNumberGenerator trackingNumbers;

    public ParcelService(ParcelRepository parcels, ParcelWriter writer, DeliveryHistoryRepository history,
                         UserRepository users, TrackingNumberGenerator trackingNumbers) {
        this.parcels = parcels;
        this.writer = writer;
        this.history = history;
        this.users = users;
        this.trackingNumbers = trackingNumbers;
    }

    /**
     * Registers a parcel for the logged-in Seller. Not @Transactional itself: each attempt runs in
     * its own transaction inside {@link ParcelWriter}, so a tracking-number collision can be retried.
     */
    @PreAuthorize("hasRole('SELLER')")
    public Parcel register(String sellerEmail, ParcelForm form) {
        User seller = requireSeller(sellerEmail);
        String name = form.getRecipientName().trim();
        String phone = form.getRecipientPhone().trim();
        String address = form.getRecipientAddress().trim();

        for (int attempt = 1; attempt <= MAX_TRACKING_NUMBER_ATTEMPTS; attempt++) {
            String trackingNumber = trackingNumbers.next();
            if (parcels.existsByTrackingNumber(trackingNumber)) {
                continue;
            }
            try {
                Parcel saved = writer.insert(seller.getId(), trackingNumber, name, phone, address, form.getWeight());
                // Log ids only: never the recipient's name, phone or address.
                log.info("Parcel registered id={} sellerId={}", saved.getId(), seller.getId());
                return saved;
            } catch (DataIntegrityViolationException e) {
                // Almost certainly a tracking-number collision (the UNIQUE constraint caught it).
                log.warn("Tracking number collision on attempt {}", attempt);
            }
        }
        log.error("Could not generate a unique tracking number after {} attempts", MAX_TRACKING_NUMBER_ATTEMPTS);
        throw new ParcelRegistrationException("The parcel could not be registered. Please try again.");
    }

    /** The Seller's own parcels, newest first. A blank query lists everything. */
    @PreAuthorize("hasRole('SELLER')")
    @Transactional(readOnly = true)
    public List<Parcel> listOwn(String sellerEmail, String query) {
        User seller = requireSeller(sellerEmail);
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return parcels.findBySellerIdOrderByCreatedAtDescIdDesc(seller.getId());
        }
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            trimmed = trimmed.substring(0, MAX_SEARCH_LENGTH);
        }
        return parcels.searchOwn(seller.getId(), likePattern(trimmed));
    }

    /** One of the Seller's own parcels. Someone else's parcel looks exactly like a missing one. */
    @PreAuthorize("hasRole('SELLER')")
    @Transactional(readOnly = true)
    public ParcelDetail getOwn(Long parcelId, String sellerEmail) {
        User seller = requireSeller(sellerEmail);
        Parcel parcel = parcels.findByIdAndSellerId(parcelId, seller.getId())
                .orElseThrow(SellerParcelNotFoundException::new);
        List<HistoryEntry> entries = history.findByParcelIdOrderByChangedAtAscIdAsc(parcel.getId()).stream()
                .map(ParcelService::toEntry)
                .toList();
        return new ParcelDetail(parcel, entries);
    }

    private static HistoryEntry toEntry(DeliveryHistory row) {
        return new HistoryEntry(row.getStatus().getLabel(), row.getChangedAt());
    }

    /** Builds a lower-case LIKE pattern in which the user's % _ and ! are matched literally. */
    static String likePattern(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder("%");
        for (char c : lower.toCharArray()) {
            if (c == '%' || c == '_' || c == LIKE_ESCAPE) {
                sb.append(LIKE_ESCAPE);
            }
            sb.append(c);
        }
        return sb.append('%').toString();
    }

    private User requireSeller(String email) {
        if (email == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        User user = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new AccessDeniedException("Unknown user"));
        if (!user.isEnabled() || user.getRole() != Role.SELLER) {
            throw new AccessDeniedException("Seller role required");
        }
        return user;
    }
}
