package com.parceltrack.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Rider pages. All rules (assignment, status values, uploads) are enforced in DeliveryService. */
@Controller
@RequestMapping("/rider/parcels")
public class DeliveryController {

    private static final Logger log = LoggerFactory.getLogger(DeliveryController.class);

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public String myParcels(Authentication auth, Model model) {
        model.addAttribute("parcels", deliveryService.listAssignedParcels(auth.getName()));
        return "rider/parcels";
    }

    @GetMapping("/{parcelId}")
    public String parcel(@PathVariable Long parcelId, Authentication auth, Model model) {
        model.addAttribute("detail", deliveryService.getAssignedParcel(parcelId, auth.getName()));
        return "rider/parcel-detail";
    }

    @PostMapping("/{parcelId}/status")
    public String updateStatus(@PathVariable Long parcelId,
                               @RequestParam("status") String status,
                               @RequestParam(value = "remarks", required = false) String remarks,
                               Authentication auth,
                               RedirectAttributes redirect) {
        try {
            DeliveryStatus updated = deliveryService.updateStatus(parcelId, status, remarks, auth.getName());
            redirect.addFlashAttribute("success", "Status changed to " + updated.getLabel() + ".");
        } catch (DeliveryRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rider/parcels/" + parcelId;
    }

    @PostMapping("/{parcelId}/proof")
    public String uploadProof(@PathVariable Long parcelId,
                              @RequestParam(value = "photo", required = false) MultipartFile photo,
                              Authentication auth,
                              RedirectAttributes redirect) {
        try {
            deliveryService.uploadProof(parcelId, photo, auth.getName());
            redirect.addFlashAttribute("success", "Proof-of-delivery photo uploaded.");
        } catch (DeliveryRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (FileStorageException e) {
            log.error("Proof upload storage failure for parcel {}", parcelId, e);
            redirect.addFlashAttribute("error", "The photo could not be saved. Please try again later.");
        }
        return "redirect:/rider/parcels/" + parcelId;
    }

    @GetMapping("/{parcelId}/proof")
    public ResponseEntity<Resource> proof(@PathVariable Long parcelId, Authentication auth) {
        return ProofResponses.image(deliveryService.loadProof(parcelId, auth.getName()));
    }
}
