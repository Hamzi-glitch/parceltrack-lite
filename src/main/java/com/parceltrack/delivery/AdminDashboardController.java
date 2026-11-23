package com.parceltrack.delivery;

import com.parceltrack.user.Role;
import com.parceltrack.user.UserController;
import com.parceltrack.user.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** Admin dashboard (parcel counts) and parcel assignment. Admin-only via SecurityConfig and the services. */
@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final DeliveryService deliveryService;
    private final AssignmentService assignmentService;
    private final UserRepository userRepository;

    public AdminDashboardController(DeliveryService deliveryService,
                                    AssignmentService assignmentService,
                                    UserRepository userRepository) {
        this.deliveryService = deliveryService;
        this.assignmentService = assignmentService;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        Map<DeliveryStatus, Long> counts = deliveryService.statusCounts(auth.getName());
        model.addAttribute("statusCounts", counts);
        model.addAttribute("totalParcels", counts.values().stream().mapToLong(Long::longValue).sum());
        // M1's "Create Rider" section of the same page.
        model.addAttribute("riders", userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.RIDER)
                .toList());
        model.addAttribute("rider", new UserController.RiderRequest());
        return "admin/dashboard";
    }

    @GetMapping("/parcels")
    public String parcels(Authentication auth, Model model) {
        model.addAttribute("parcels", assignmentService.listAssignableParcels(auth.getName()));
        model.addAttribute("riderOptions", assignmentService.listActiveRiders(auth.getName()));
        return "admin/parcels";
    }

    @PostMapping("/parcels/{parcelId}/assign")
    public String assign(@PathVariable Long parcelId,
                         @RequestParam(value = "riderId", required = false) Long riderId,
                         Authentication auth,
                         RedirectAttributes redirect) {
        try {
            assignmentService.assign(parcelId, riderId, auth.getName());
            redirect.addFlashAttribute("success", "Parcel assigned.");
        } catch (DeliveryRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/parcels";
    }

    @GetMapping("/parcels/{parcelId}/proof")
    public ResponseEntity<Resource> proof(@PathVariable Long parcelId, Authentication auth) {
        return ProofResponses.image(deliveryService.loadProof(parcelId, auth.getName()));
    }
}
