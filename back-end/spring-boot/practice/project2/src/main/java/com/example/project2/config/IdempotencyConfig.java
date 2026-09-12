package com.example.project2.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#config
 * Binds the {@code idempotency.*} properties and turns on scheduling for the cleanup sweep.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfig {
}
