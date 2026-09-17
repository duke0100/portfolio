package com.example.project2.lifecycle;

import com.example.project2.config.GracefulShutdownProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Interview topic: docs/interview/spring-boot/04-graceful-shutdown.md#draining-before-the-server-stops
 * (this class is the ContextClosedEvent code sample in that section)
 *
 * <p>Spring publishes {@code ContextClosedEvent} before it stops any lifecycle bean, so this runs
 * while the web server is still accepting connections. That is the only window in which we can
 * keep answering requests the load balancer has not stopped sending yet.
 *
 * <p>Without the pause there is a race: the pod is killed, the load balancer still has us in its
 * pool for a second or two, and every request it routes here gets a connection refused.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class ShutdownDrainListener implements ApplicationListener<ContextClosedEvent> {

    private final ApplicationContext applicationContext;
    private final GracefulShutdownProperties properties;
    private final ShutdownState shutdownState;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        // The management server runs in a child context, and a child event reaches the parent's
        // listeners too. Without this check the deregistration pause would run twice per shutdown.
        if (!applicationContext.equals(event.getApplicationContext())) {
            return;
        }
        shutdownState.markShutdownStarted();

        // Readiness goes OUT_OF_SERVICE, so /actuator/health/readiness answers 503 on the next poll
        // while /actuator/health/liveness stays UP - "stop sending traffic", not "restart me".
        AvailabilityChangeEvent.publish(event.getApplicationContext(), ReadinessState.REFUSING_TRAFFIC);

        Duration delay = properties.getDeregistrationDelay();
        if (delay.isZero() || delay.isNegative()) {
            log.info("Shutdown: refusing traffic, no deregistration delay configured");
            return;
        }

        log.info("Shutdown: refusing traffic, still serving for {} so the load balancer can drop us", delay);
        waitForDeregistration(delay);
        log.info("Shutdown: deregistration window over, {} request(s) still in flight",
                shutdownState.activeHttpRequests());
    }

    /**
     * Sleeps in small steps so the log shows the in-flight count falling. An interrupt here ends
     * the wait early rather than swallowing it - the JVM is on its way out either way.
     */
    private void waitForDeregistration(Duration delay) {
        long deadlineNanos = System.nanoTime() + delay.toNanos();
        long stepNanos = Math.max(properties.getDrainLogInterval().toNanos(), 1L);
        try {
            long remaining;
            while ((remaining = deadlineNanos - System.nanoTime()) > 0) {
                TimeUnit.NANOSECONDS.sleep(Math.min(remaining, stepNanos));
                log.info("Shutdown: {} request(s) in flight", shutdownState.activeHttpRequests());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Shutdown: deregistration wait interrupted, closing the context now");
        }
    }
}
