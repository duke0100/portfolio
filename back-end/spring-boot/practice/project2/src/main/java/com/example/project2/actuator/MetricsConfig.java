package com.example.project2.actuator;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Interview topic: docs/interview/spring-boot/01-spring-boot-actuator.md#custom-metrics
 * Metrics setup that Spring Boot doesn't enable automatically.
 */
@Configuration
public class MetricsConfig {

    /** Without this bean, {@code @Timed} annotations are silently ignored and do nothing. */
    @Bean
    public TimedAspect timedAspect(MeterRegistry meterRegistry) {
        return new TimedAspect(meterRegistry);
    }

    /** Tags every metric with the app name so this service's data is distinguishable on shared dashboards. */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name}") String applicationName) {
        return registry -> registry.config().commonTags("application", applicationName);
    }
}
