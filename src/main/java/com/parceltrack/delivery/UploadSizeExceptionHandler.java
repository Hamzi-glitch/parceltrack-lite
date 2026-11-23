package com.parceltrack.delivery;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Files over spring.servlet.multipart.max-file-size are rejected before any controller runs.
 * Send the Rider back to the parcel page with a clear message instead of a generic error.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadSizeExceptionHandler {

    private static final Pattern PROOF_URL = Pattern.compile("^/rider/parcels/(\\d{1,18})/proof$");

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Matcher m = PROOF_URL.matcher(path);
        if (m.matches()) {
            FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
            flash.put("error", "The photo is too large. Please upload a smaller JPG or PNG.");
            RequestContextUtils.saveOutputFlashMap("/rider/parcels/" + m.group(1), request, response);
            return "redirect:/rider/parcels/" + m.group(1);
        }
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        return "error/400";
    }
}
