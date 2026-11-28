package com.parceltrack.parcel;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Public tracking page: no login. It only ever receives a TrackingView (status history), never a
 * Parcel. A malformed number and an unknown number give the same generic answer.
 * Rate limiting against guessing is added in Week 2.
 */
@Controller
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/track")
    public String track(@RequestParam(value = "number", required = false) String number,
                        Model model, HttpServletResponse response) {
        if (number == null || number.isBlank()) {
            return "tracking/track";
        }
        Optional<TrackingService.TrackingView> view = trackingService.find(number);
        if (view.isPresent()) {
            model.addAttribute("view", view.get());
        } else {
            // Same page, same message and same status for every kind of miss. The typed value is not echoed back.
            response.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("notFound", true);
        }
        return "tracking/track";
    }
}
