package com.example.project2.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Interview topic: docs/interview/spring-boot/04-graceful-shutdown.md#config
 * The knobs of this app's shutdown sequence, bound from the {@code catalog.shutdown.*} properties.
 *
 * <p>{@code server.shutdown=graceful} only covers requests that already arrived. The
 * deregistration delay below covers the requests the load balancer is still about to send us.
 *
 * <p>Every value has a default here, so a test that shadows application.properties still gets a
 * sane shutdown instead of a zero-length one.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "catalog.shutdown")
public class GracefulShutdownProperties {

    /** How long we keep serving after SIGTERM so the load balancer notices we are out of service. */
    private Duration deregistrationDelay = Duration.ofSeconds(5);

    /** How often the drain logs the number of requests still running. */
    private Duration drainLogInterval = Duration.ofSeconds(1);

    /** Ceiling on the drain probe's hold time, so the probe can never outlast the grace period. */
    private Duration maxProbeHold = Duration.ofSeconds(30);
}
