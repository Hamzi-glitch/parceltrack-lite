package com.parceltrack.common;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/**
 * Turns unexpected exceptions into generic error pages. Users never see stack traces,
 * SQL errors or exception messages; the details go to the server log with a reference ID.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Let Spring Security handle these so it returns 403 / redirects to login. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityExceptions(RuntimeException ex) {
        throw ex;
    }

    /** e.g. /parcels/abc where a number was expected (also covers SQL-injection-style IDs). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleTypeMismatch(HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return "error/400";
    }

    @ExceptionHandler(Exception.class)
    public String handleAny(Exception ex, HttpServletResponse response) {
        HttpStatusCode status = (ex instanceof ErrorResponse er)
                ? er.getStatusCode()
                : HttpStatus.INTERNAL_SERVER_ERROR;
        response.setStatus(status.value());

        if (status.is5xxServerError()) {
            String ref = UUID.randomUUID().toString().substring(0, 8);
            log.error("Unhandled error [ref={}]", ref, ex);
        }
        return switch (status.value()) {
            case 400 -> "error/400";
            case 403 -> "error/403";
            case 404 -> "error/404";
            default -> "error";
        };
    }
}
