package com.parceltrack.delivery;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Serves a stored proof photo as an image only: never as HTML or a download that could run. */
final class ProofResponses {

    private ProofResponses() {
    }

    static ResponseEntity<Resource> image(DeliveryService.ProofFile proof) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(proof.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(proof.path()));
    }
}
