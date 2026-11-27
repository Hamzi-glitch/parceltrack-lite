package com.parceltrack.parcel;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Seller pages. The URLs are limited to Sellers in SecurityConfig, and ParcelService checks the
 * role and the parcel owner again, so nothing here relies on hidden links.
 */
@Controller
public class ParcelController {

    private final ParcelService parcelService;

    public ParcelController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @GetMapping("/seller/parcels")
    public String myParcels(@RequestParam(value = "q", required = false) String q,
                            Authentication auth, Model model) {
        model.addAttribute("parcels", parcelService.listOwn(auth.getName(), q));
        model.addAttribute("q", q == null ? "" : q.trim());
        return "seller/parcels";
    }

    @GetMapping("/seller/parcels/new")
    public String newParcelForm(Model model) {
        if (!model.containsAttribute("parcelForm")) {
            model.addAttribute("parcelForm", new ParcelForm());
        }
        return "seller/parcel-form";
    }

    @PostMapping("/seller/parcels")
    public String register(@Validated @ModelAttribute("parcelForm") ParcelForm form,
                           BindingResult bindingResult,
                           Authentication auth,
                           Model model,
                           RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            return "seller/parcel-form";
        }
        try {
            Parcel parcel = parcelService.register(auth.getName(), form);
            redirect.addFlashAttribute("success",
                    "Parcel registered. Its tracking number is " + parcel.getTrackingNumber() + ".");
            return "redirect:/seller/parcels/" + parcel.getId();
        } catch (ParcelRegistrationException e) {
            model.addAttribute("error", e.getMessage());
            return "seller/parcel-form";
        }
    }

    /** A non-numeric id (for example an injection attempt) is rejected with a 400 by GlobalExceptionHandler. */
    @GetMapping("/seller/parcels/{parcelId}")
    public String parcel(@PathVariable Long parcelId, Authentication auth, Model model) {
        model.addAttribute("detail", parcelService.getOwn(parcelId, auth.getName()));
        return "seller/parcel-detail";
    }
}
