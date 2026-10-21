package com.nexus.gateway.carrier;

/** A carrier refused or failed a call; the message is the carrier's own when it gave one. */
public class CarrierException extends RuntimeException {

    private final int status;

    public CarrierException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
