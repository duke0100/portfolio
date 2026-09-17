package com.example.project2.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Interview topic: docs/interview/spring-boot/04-graceful-shutdown.md#config
 * Binds the {@code catalog.shutdown.*} properties for the shutdown listener and the drain probe.
 */
@Configuration
@EnableConfigurationProperties(GracefulShutdownProperties.class)
public class GracefulShutdownConfig {
}
