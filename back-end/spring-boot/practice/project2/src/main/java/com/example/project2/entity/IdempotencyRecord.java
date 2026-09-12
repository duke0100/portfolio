package com.example.project2.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#the-store
 * One row per Idempotency-Key: what was asked for, and what we answered.
 *
 * <p>The key comes from the client, so there is no {@code @GeneratedValue} here.
 */
@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", length = 120, nullable = false, updatable = false)
    private String idempotencyKey;

    /**
     * Method and path of the first request, such as {@code POST /api/v1/checkout/orders}.
     * A key replayed against a different endpoint is a client bug, not a retry.
     */
    @Column(name = "request_target", length = 200, nullable = false, updatable = false)
    private String requestTarget;

    /** SHA-256 of the request body, so we can tell a real retry from a reused key. */
    @Column(name = "request_fingerprint", length = 64, nullable = false, updatable = false)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private IdempotencyStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    /** The successful response body as JSON. Null while the first attempt is still running. */
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#cleanup
     * After this the row is deleted and the same key may be used again.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }
}
