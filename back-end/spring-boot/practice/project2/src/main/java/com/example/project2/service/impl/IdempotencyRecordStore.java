package com.example.project2.service.impl;

import com.example.project2.entity.IdempotencyRecord;
import com.example.project2.entity.IdempotencyStatus;
import com.example.project2.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#claim-run-record
 * Every method here commits on its own, so the claim is visible to a parallel retry before the
 * order is even created.
 *
 * <p>It is a separate bean because a {@code REQUIRES_NEW} call on {@code this} would go straight to
 * the method and skip the proxy that starts the transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyRecordStore {

    private final IdempotencyRecordRepository repository;

    /** True when this call won the key, false when another request already holds it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String key, String target, String fingerprint, Instant now, Duration ttl) {
        return repository.insertClaim(key, target, fingerprint, now, now.plus(ttl)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyRecord> find(String key) {
        return repository.findById(key);
    }

    /** Stores the response so later retries can be answered without touching the business logic. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, int responseStatus, String responseBody) {
        repository.findById(key).ifPresent(stored -> {
            stored.setStatus(IdempotencyStatus.COMPLETED);
            stored.setResponseStatus(responseStatus);
            stored.setResponseBody(responseBody);
            stored.setCompletedAt(Instant.now());
        });
    }

    /**
     * Drops the claim after a failed attempt. Without this the client would be locked out of its
     * own key until the TTL ran out, even though nothing was created.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String key) {
        repository.deleteById(key);
        log.debug("Released idempotency key {}", key);
    }

    /**
     * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#cleanup
     * Number of expired rows removed by this sweep.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeExpired(Instant now, int batchSize) {
        return repository.deleteExpired(now, batchSize);
    }
}
