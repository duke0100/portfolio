package com.example.project2.service.impl;

import com.example.project2.config.IdempotencyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#cleanup
 * Sweeps expired keys so the table stays roughly "one TTL of traffic" in size.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupJob {

    private final IdempotencyRecordStore store;
    private final IdempotencyProperties properties;

    /**
     * fixedDelay, not fixedRate: the next sweep starts after the previous one finished, so a slow
     * delete cannot pile runs on top of each other.
     */
    @Scheduled(fixedDelayString = "${idempotency.cleanup-interval:PT15M}")
    public void purgeExpiredKeys() {
        int deleted = store.purgeExpired(Instant.now(), properties.getCleanupBatchSize());
        if (deleted > 0) {
            log.info("Purged {} expired idempotency records", deleted);
        }
    }
}
