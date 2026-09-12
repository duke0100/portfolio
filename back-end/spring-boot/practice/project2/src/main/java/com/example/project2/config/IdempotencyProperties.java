package com.example.project2.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#config
 * The three knobs of the idempotency store, bound from the {@code idempotency.*} properties.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "idempotency")
public class IdempotencyProperties {

    /** How long a key is remembered. Must outlive the longest retry window of your clients. */
    private Duration ttl = Duration.ofHours(24);

    /** How often expired keys are swept. */
    private Duration cleanupInterval = Duration.ofMinutes(15);

    /** Rows deleted per sweep, so one run can never lock the whole table. */
    private int cleanupBatchSize = 500;
}
