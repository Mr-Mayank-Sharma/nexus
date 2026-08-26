package com.nexus.oms.service.ai;

import java.util.List;
import java.util.Map;

/**
 * Thrown when a model version fails the P1.4 deployment validation gate
 * and no force override was supplied. Carries the gate's failure reasons
 * and numeric detail so the controller can return a useful 422 payload.
 */
public class AiGateBlockedException extends RuntimeException {

    private final List<String> failures;
    private final Map<String, Object> detail;

    public AiGateBlockedException(List<String> failures, Map<String, Object> detail) {
        super("Deployment blocked by validation gate: " + String.join("; ", failures));
        this.failures = List.copyOf(failures);
        this.detail = detail;
    }

    public List<String> getFailures() {
        return failures;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }
}
