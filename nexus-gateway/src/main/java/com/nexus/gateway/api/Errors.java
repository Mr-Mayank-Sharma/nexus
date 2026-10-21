package com.nexus.gateway.api;

import com.nexus.gateway.carrier.CarrierException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Errors as {"error": "..."} with a status that says whose problem it is. */
@RestControllerAdvice
public class Errors {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> notSetUp(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }

    /** The carrier said no (4xx: 422 with its reason) or failed (502). */
    @ExceptionHandler(CarrierException.class)
    public ResponseEntity<Map<String, String>> carrier(CarrierException e) {
        int status = e.status() >= 400 && e.status() < 500 ? 422 : 502;
        return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
    }
}
