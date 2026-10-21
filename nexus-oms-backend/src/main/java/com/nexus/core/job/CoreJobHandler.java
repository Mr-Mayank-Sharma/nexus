package com.nexus.core.job;

import java.util.Map;

/**
 * Work a {@code service_job} runs. The job's {@code service_name} is the Spring bean name of the handler. Return a short
 * summary of what was done (kept as the run's results); throw to record the run as failed.
 */
public interface CoreJobHandler {

    String run(Map<String, String> parameters);
}
