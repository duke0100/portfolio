package com.example.project2.service;

import com.example.project2.dto.response.DrainProbeResponse;
import com.example.project2.dto.response.LifecycleStateResponse;

/**
 * Interview topic: docs/interview/spring-boot/04-graceful-shutdown.md#watching-the-shutdown
 * Read-only view of this instance's shutdown state, plus the slow request used to prove the drain.
 */
public interface ShutdownProbeService {

    LifecycleStateResponse currentState();

    /**
     * Holds one request open for the given time, which is what an in-flight checkout or report
     * looks like to the shutdown sequence.
     *
     * @param holdMillis how long to hold, capped by {@code catalog.shutdown.max-probe-hold}
     */
    DrainProbeResponse hold(long holdMillis);
}
