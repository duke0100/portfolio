package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.config.GracefulShutdownProperties;
import com.example.project2.dto.response.DrainProbeResponse;
import com.example.project2.dto.response.LifecycleStateResponse;
import com.example.project2.lifecycle.BackgroundWorkGate;
import com.example.project2.lifecycle.ShutdownState;
import com.example.project2.service.ShutdownProbeService;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Interview topic: docs/interview/spring-boot/04-graceful-shutdown.md#proving-the-drain
 * (this class is the code sample in that section)
 *
 * <p>The hold is the only honest way to see the drain work: you need a request that is still
 * running when SIGTERM lands. The hold is capped by {@code catalog.shutdown.max-probe-hold} so a
 * careless caller cannot park a Tomcat thread past the grace period and force a hard kill.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShutdownProbeServiceImpl implements ShutdownProbeService {

    /** Gauge of probes in flight. The drain test waits on this instead of sleeping. */
    private static final String ACTIVE_PROBES_METER = "catalog.shutdown.probe.active";

    private static final String COMPLETED_PROBES_METER = "catalog.shutdown.probe.completed";

    private final GracefulShutdownProperties properties;
    private final ShutdownState shutdownState;
    private final BackgroundWorkGate backgroundWorkGate;
    private final ApplicationAvailability applicationAvailability;
    private final MeterRegistry meterRegistry;
    private final Environment environment;

    @Override
    public LifecycleStateResponse currentState() {
        return LifecycleStateResponse.builder()
                .shutdownMode(environment.getProperty("server.shutdown", "immediate"))
                .deregistrationDelay(properties.getDeregistrationDelay().toString())
                .shuttingDown(shutdownState.isShuttingDown())
                .shutdownStartedAt(shutdownState.getShutdownStartedAt())
                .readinessState(applicationAvailability.getReadinessState().name())
                .activeHttpRequests(shutdownState.activeHttpRequests())
                .backgroundJobsAccepting(backgroundWorkGate.isAccepting())
                .build();
    }

    @Override
    public DrainProbeResponse hold(long holdMillis) {
        long maxHoldMillis = properties.getMaxProbeHold().toMillis();
        if (holdMillis <= 0 || holdMillis > maxHoldMillis) {
            throw BusinessException.badRequest(
                    "holdMillis must be between 1 and " + maxHoldMillis);
        }

        boolean startedDuringShutdown = shutdownState.isShuttingDown();
        LongTaskTimer.Sample sample = LongTaskTimer.builder(ACTIVE_PROBES_METER)
                .description("Drain probe requests currently in flight")
                .register(meterRegistry)
                .start();
        long startNanos = System.nanoTime();
        try {
            TimeUnit.MILLISECONDS.sleep(holdMillis);
        } catch (InterruptedException e) {
            // Restore the flag; the caller sees a 503 rather than a truncated 200.
            Thread.currentThread().interrupt();
            throw new BusinessException("Drain probe interrupted by shutdown",
                    HttpStatus.SERVICE_UNAVAILABLE, "SHUTDOWN_INTERRUPTED");
        } finally {
            sample.stop();
        }

        boolean finishedDuringShutdown = shutdownState.isShuttingDown();
        meterRegistry.counter(COMPLETED_PROBES_METER,
                "duringShutdown", String.valueOf(finishedDuringShutdown)).increment();
        if (finishedDuringShutdown) {
            log.info("Drain probe finished after shutdown started, held {}ms", holdMillis);
        }

        return DrainProbeResponse.builder()
                .requestedHoldMillis(holdMillis)
                .actualHoldMillis((System.nanoTime() - startNanos) / 1_000_000)
                .startedDuringShutdown(startedDuringShutdown)
                .finishedDuringShutdown(finishedDuringShutdown)
                .build();
    }
}
