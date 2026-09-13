package com.example.project2.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#config
 * Binds the {@code catalog.snapshot.*} properties for the fan-out pool.
 */
@Configuration
@EnableConfigurationProperties(CatalogSnapshotProperties.class)
public class CatalogConcurrencyConfig {
}
