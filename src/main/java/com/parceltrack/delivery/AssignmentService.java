package com.parceltrack.delivery;

import com.parceltrack.delivery.ParcelGateway.ParcelSummary;
import com.parceltrack.user.Role;
import com.parceltrack.user.User;
import com.parceltrack.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin-only: assigning parcels to Riders. */
@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    public record AssignableParcel(Long parcelId,
                                   String trackingNumber,
                                   DeliveryStatus status,
                                   Long currentRiderId,
                                   String currentRiderName) {
    }

    public record RiderOption(Long id, String fullName) {
    }

    private final ParcelGateway parcels;
    private final ParcelAssignmentRepository assignments;
    private final DeliveryHistoryRepository history;
    private final UserRepository userRepository;
    private final CurrentUsers currentUsers;

    public AssignmentService(ParcelGateway parcels,
                             ParcelAssignmentRepository assignments,
                             DeliveryHistoryRepository history,
                             UserRepository userRepository,
                             CurrentUsers currentUsers) {
        this.parcels = parcels;
        this.assignments = assignments;
        this.history = history;
        this.userRepository = userRepository;
        this.currentUsers = currentUsers;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AssignableParcel> listAssignableParcels(String adminEmail) {
        currentUsers.require(adminEmail, Role.ADMIN);
        List<ParcelSummary> open = parcels.findAssignable();
        if (open.isEmpty()) {
            return List.of();
        }
        Map<Long, ParcelAssignment> current = assignments
                .findCurrentByParcelIds(open.stream().map(ParcelSummary::id).toList()).stream()
                .collect(Collectors.toMap(ParcelAssignment::getParcelId, Function.identity()));
        Map<Long, String> riderNames = currentUsers.namesById(
                current.values().stream().map(ParcelAssignment::getRiderId).toList());

        return open.stream().map(p -> {
            ParcelAssignment a = current.get(p.id());
            Long riderId = a == null ? null : a.getRiderId();
            return new AssignableParcel(p.id(), p.trackingNumber(), p.status(), riderId,
                    riderId == null ? null : riderNames.get(riderId));
        }).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<RiderOption> listActiveRiders(String adminEmail) {
        currentUsers.require(adminEmail, Role.ADMIN);
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.RIDER && u.isEnabled())
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new RiderOption(u.getId(), u.getFullName()))
                .toList();
    }

    /**
     * Assigns (or reassigns) a parcel to a Rider. The parcel moves to ASSIGNED and a history
     * row is written, all in one transaction.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void assign(Long parcelId, Long riderId, String adminEmail) {
        User admin = currentUsers.require(adminEmail, Role.ADMIN);
        if (parcelId == null) {
            throw new ParcelNotFoundException();
        }
        ParcelSummary parcel = parcels.findById(parcelId).orElseThrow(ParcelNotFoundException::new);
        if (!parcel.status().isAssignable()) {
            throw new DeliveryRuleException("A delivered parcel cannot be reassigned.");
        }
        User rider = Optional.ofNullable(riderId)
                .flatMap(userRepository::findById)
                .filter(u -> u.getRole() == Role.RIDER && u.isEnabled())
                .orElseThrow(() -> new DeliveryRuleException("Please choose an active Rider."));

        Optional<ParcelAssignment> current = assignments.findFirstByParcelIdOrderByIdDesc(parcelId);
        if (current.isPresent() && current.get().getRiderId().equals(rider.getId())) {
            throw new DeliveryRuleException("This parcel is already assigned to that Rider.");
        }

        assignments.save(new ParcelAssignment(parcelId, rider.getId(), admin.getId()));
        parcels.updateStatus(parcelId, DeliveryStatus.ASSIGNED);
        history.save(new DeliveryHistory(parcelId, DeliveryStatus.ASSIGNED, admin.getId(),
                current.isPresent() ? "Reassigned to another Rider" : "Assigned to a Rider"));

        log.info("Parcel {} assigned to rider {} by admin {}", parcelId, rider.getId(), admin.getId());
    }
}
