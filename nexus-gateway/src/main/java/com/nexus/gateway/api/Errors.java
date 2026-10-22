package com.nexus.gateway.api;

import com.nexus.gateway.carrier.CarrierException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Errors as {"error": "..."} with a status that says whose problem it is. */
@RestControllerAdvice
public class Errors {

    /** The request was wrong: 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException problem) {
        return ResponseEntity.badRequest().body(Map.of("error", problem.getMessage()));
    }

    /** Something isn't set up yet (no account, no template): 409. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> notSetUp(IllegalStateException problem) {
        return ResponseEntity.status(409).body(Map.of("error", problem.getMessage()));
    }

    /** The carrier said no (4xx: 422 with its reason) or failed (502). */
    @ExceptionHandler(CarrierException.class)
    public ResponseEntity<Map<String, String>> carrier(CarrierException problem) {
        boolean carrierRefused = problem.status() >= 400 && problem.status() < 500;
        return ResponseEntity.status(carrierRefused ? 422 : 502).body(Map.of("error", problem.getMessage()));
    }
}
