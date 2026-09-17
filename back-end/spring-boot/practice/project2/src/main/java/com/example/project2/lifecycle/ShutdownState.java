package com.example.project2.lifecycle;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Interview topic: docs/interview/spring-boot/04-graceful-shutdown.md#draining-before-the-server-stops
 * (this bean is the shutdown flag the listener sets and the lifecycle endpoint reads)
 *
 * <p>The flag is a plain {@code volatile} field on purpose. Shutdown is a per-process event, so
 * there is nothing here to share with the other instances - each one flips its own flag when its
 * own SIGTERM arrives.
 */
@Component
@RequiredArgsConstructor
public class ShutdownState {

    /** Micrometer's built-in long task timer for requests currently being handled. */
    private static final String ACTIVE_REQUESTS_METER = "http.server.requests.active";

    private final MeterRegistry meterRegistry;

    /** Written once by the shutdown listener, read by request threads, hence volatile. */
    private volatile Instant shutdownStartedAt;

    public void markShutdownStarted() {
        if (shutdownStartedAt == null) {
            shutdownStartedAt = Instant.now();
        }
    }

    public boolean isShuttingDown() {
        return shutdownStartedAt != null;
    }

    public Instant getShutdownStartedAt() {
        return shutdownStartedAt;
    }

    /**
     * HTTP requests being handled right now, the caller's own request included. This is what the
     * drain waits for, so a number that never falls is the signal that a handler is stuck.
     */
    public long activeHttpRequests() {
        return meterRegistry.find(ACTIVE_REQUESTS_METER).longTaskTimers().stream()
                .mapToLong(LongTaskTimer::activeTasks)
                .sum();
    }
}
