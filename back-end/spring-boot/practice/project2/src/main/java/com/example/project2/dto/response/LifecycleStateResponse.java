package com.example.project2.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Interview topic: docs/interview/spring-boot/04-graceful-shutdown.md#watching-the-shutdown
 * What this instance is doing right now: still serving, draining, or already refusing traffic.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LifecycleStateResponse {

    /** Value of the {@code server.shutdown} property: "graceful" or "immediate". */
    private String shutdownMode;

    /** Configured deregistration delay, e.g. "PT5S". */
    private String deregistrationDelay;

    private boolean shuttingDown;

    /** Null until SIGTERM arrives. */
    private Instant shutdownStartedAt;

    /** ACCEPTING_TRAFFIC while healthy, REFUSING_TRAFFIC once the context starts closing. */
    private String readinessState;

    /** Requests being handled right now, this one included. */
    private long activeHttpRequests;

    /** False once the background gate has closed, so no scheduled sweep will start. */
    private boolean backgroundJobsAccepting;
}
