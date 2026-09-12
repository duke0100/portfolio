package com.example.project2.repository;

import com.example.project2.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#claim-run-record
     * Returns 1 when this call won the key and 0 when someone else already holds it.
     *
     * <p>{@code ON CONFLICT DO NOTHING} keeps the race in the database. A read-then-insert in Java
     * would let two parallel retries both pass the read and place two orders.
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records
                (idempotency_key, request_target, request_fingerprint, status, created_at, expires_at)
            VALUES (:key, :target, :fingerprint, 'IN_PROGRESS', :now, :expiresAt)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertClaim(@Param("key") String key,
                    @Param("target") String target,
                    @Param("fingerprint") String fingerprint,
                    @Param("now") Instant now,
                    @Param("expiresAt") Instant expiresAt);

    /**
     * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#cleanup
     * Deletes at most {@code batchSize} expired rows per run, so the job never takes locks on a
     * huge set or blocks a live checkout behind it.
     */
    @Modifying
    @Query(value = """
            DELETE FROM idempotency_records
            WHERE idempotency_key IN (
                SELECT idempotency_key
                FROM idempotency_records
                WHERE expires_at < :now
                ORDER BY expires_at
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteExpired(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
