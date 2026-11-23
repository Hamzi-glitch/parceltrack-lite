package com.parceltrack.delivery;

import com.parceltrack.delivery.ParcelGateway.ParcelSummary;
import com.parceltrack.user.Role;
import com.parceltrack.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rider delivery work and Admin dashboard data. Every Rider operation checks, in this
 * service, that the parcel is CURRENTLY assigned to that Rider. If not, the Rider gets the
 * same generic "not found" as for a parcel that does not exist.
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    public record AssignedParcel(ParcelSummary parcel, LocalDateTime assignedAt) {
    }

    public record HistoryEntry(DeliveryStatus status, LocalDateTime changedAt, String changedBy, String remarks) {
    }

    public record ParcelDetail(ParcelSummary parcel,
                               List<HistoryEntry> history,
                               Set<DeliveryStatus> allowedNextStatuses,
                               boolean hasProof,
                               boolean canUploadProof) {
    }

    public record ProofFile(Path path, String contentType) {
    }

    private final ParcelGateway parcels;
    private final ParcelAssignmentRepository assignments;
    private final DeliveryHistoryRepository history;
    private final DeliveryProofRepository proofs;
    private final FileStorageService fileStorage;
    private final CurrentUsers currentUsers;

    public DeliveryService(ParcelGateway parcels,
                           ParcelAssignmentRepository assignments,
                           DeliveryHistoryRepository history,
                           DeliveryProofRepository proofs,
                           FileStorageService fileStorage,
                           CurrentUsers currentUsers) {
        this.parcels = parcels;
        this.assignments = assignments;
        this.history = history;
        this.proofs = proofs;
        this.fileStorage = fileStorage;
        this.currentUsers = currentUsers;
    }

    // ---------------------------------------------------------------- Rider

    @PreAuthorize("hasRole('RIDER')")
    @Transactional(readOnly = true)
    public List<AssignedParcel> listAssignedParcels(String riderEmail) {
        User rider = currentUsers.require(riderEmail, Role.RIDER);
        List<ParcelAssignment> mine = assignments.findCurrentByRiderId(rider.getId());
        Map<Long, ParcelSummary> byId = parcels.findByIds(mine.stream().map(ParcelAssignment::getParcelId).toList())
                .stream().collect(Collectors.toMap(ParcelSummary::id, Function.identity()));
        return mine.stream()
                .filter(a -> byId.containsKey(a.getParcelId()))
                .map(a -> new AssignedParcel(byId.get(a.getParcelId()), a.getAssignedAt()))
                .toList();
    }

    @PreAuthorize("hasRole('RIDER')")
    @Transactional(readOnly = true)
    public ParcelDetail getAssignedParcel(Long parcelId, String riderEmail) {
        User rider = currentUsers.require(riderEmail, Role.RIDER);
        ParcelSummary parcel = requireAssignedParcel(parcelId, rider);
        boolean hasProof = proofs.existsByParcelId(parcelId);
        return new ParcelDetail(parcel, historyOf(parcelId), parcel.status().riderTransitions(),
                hasProof, !hasProof && parcel.status() == DeliveryStatus.DELIVERED);
    }

    /**
     * Changes the parcel's status and writes the history row in ONE transaction: if either
     * fails, both are rolled back, so status and history can never disagree.
     */
    @PreAuthorize("hasRole('RIDER')")
    @Transactional
    public DeliveryStatus updateStatus(Long parcelId, String requestedStatus, String remarks, String riderEmail) {
        User rider = currentUsers.require(riderEmail, Role.RIDER);
        ParcelSummary parcel = requireAssignedParcel(parcelId, rider);

        DeliveryStatus next = DeliveryStatus.parse(requestedStatus)
                .orElseThrow(() -> new DeliveryRuleException("Invalid status value."));
        if (!parcel.status().canRiderMoveTo(next)) {
            throw new DeliveryRuleException("A parcel that is " + parcel.status().getLabel()
                    + " cannot be changed to " + next.getLabel() + ".");
        }
        String cleanRemarks = cleanRemarks(remarks);
        if (next == DeliveryStatus.FAILED && cleanRemarks == null) {
            throw new DeliveryRuleException("Please give a reason when a delivery fails.");
        }

        parcels.updateStatus(parcelId, next);
        history.save(new DeliveryHistory(parcelId, next, rider.getId(), cleanRemarks));

        // IDs only: never log recipient names, phones or addresses.
        log.info("Parcel {} status {} -> {} by rider {}", parcelId, parcel.status(), next, rider.getId());
        return next;
    }

    /** One JPG/PNG proof per parcel, only after the parcel is Delivered. */
    @PreAuthorize("hasRole('RIDER')")
    @Transactional
    public void uploadProof(Long parcelId, MultipartFile photo, String riderEmail) {
        User rider = currentUsers.require(riderEmail, Role.RIDER);
        ParcelSummary parcel = requireAssignedParcel(parcelId, rider);
        if (parcel.status() != DeliveryStatus.DELIVERED) {
            throw new DeliveryRuleException("Mark the parcel as Delivered before uploading proof.");
        }
        if (proofs.existsByParcelId(parcelId)) {
            throw new DeliveryRuleException("A proof-of-delivery photo has already been uploaded.");
        }

        String storedFilename = fileStorage.storeProofPhoto(photo);
        try {
            proofs.saveAndFlush(new DeliveryProof(parcelId, rider.getId(), storedFilename));
        } catch (DataIntegrityViolationException e) {
            fileStorage.deleteQuietly(storedFilename);
            throw new DeliveryRuleException("A proof-of-delivery photo has already been uploaded.");
        } catch (RuntimeException e) {
            // Database failed after the file was written: remove the file so nothing is left half-done.
            fileStorage.deleteQuietly(storedFilename);
            throw e;
        }
        log.info("Proof uploaded for parcel {} by rider {}", parcelId, rider.getId());
    }

    /** Admins may view any proof; Riders only proofs of parcels currently assigned to them. */
    @PreAuthorize("hasAnyRole('RIDER', 'ADMIN')")
    @Transactional(readOnly = true)
    public ProofFile loadProof(Long parcelId, String email) {
        User user = currentUsers.require(email, Role.RIDER, Role.ADMIN);
        if (user.getRole() == Role.RIDER) {
            requireAssignedParcel(parcelId, user);
        }
        DeliveryProof proof = proofs.findByParcelId(parcelId).orElseThrow(ParcelNotFoundException::new);
        Path path = fileStorage.resolveStored(proof.getStoredFilename());
        return new ProofFile(path, fileStorage.contentTypeOf(proof.getStoredFilename()));
    }

    // ---------------------------------------------------------------- Admin dashboard

    /** Parcel counts per status, counted from the parcels table. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Map<DeliveryStatus, Long> statusCounts(String adminEmail) {
        currentUsers.require(adminEmail, Role.ADMIN);
        return parcels.countByStatus();
    }

    // ---------------------------------------------------------------- helpers

    private ParcelSummary requireAssignedParcel(Long parcelId, User rider) {
        if (parcelId == null) {
            throw new ParcelNotFoundException();
        }
        boolean assignedToRider = assignments.findFirstByParcelIdOrderByIdDesc(parcelId)
                .map(a -> a.getRiderId().equals(rider.getId()))
                .orElse(false);
        if (!assignedToRider) {
            throw new ParcelNotFoundException();
        }
        return parcels.findById(parcelId).orElseThrow(ParcelNotFoundException::new);
    }

    private List<HistoryEntry> historyOf(Long parcelId) {
        List<DeliveryHistory> rows = history.findByParcelIdOrderByChangedAtAscIdAsc(parcelId);
        Map<Long, String> names = currentUsers.namesById(rows.stream().map(DeliveryHistory::getChangedBy).toList());
        return rows.stream()
                .sorted(Comparator.comparing(DeliveryHistory::getChangedAt).thenComparing(DeliveryHistory::getId))
                .map(h -> new HistoryEntry(h.getStatus(), h.getChangedAt(),
                        names.getOrDefault(h.getChangedBy(), "Unknown user"), h.getRemarks()))
                .toList();
    }

    static String cleanRemarks(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            return null;
        }
        String trimmed = remarks.trim();
        if (trimmed.length() > DeliveryHistory.MAX_REMARKS) {
            throw new DeliveryRuleException("Remarks must be " + DeliveryHistory.MAX_REMARKS
                    + " characters or fewer.");
        }
        return trimmed;
    }
}
