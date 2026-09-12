package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.config.IdempotencyProperties;
import com.example.project2.entity.IdempotencyRecord;
import com.example.project2.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#claim-run-record
 * Claim the key, run the action, record the response - and drop the claim if the action failed.
 *
 * <p>Deliberately not {@code @Transactional}: the claim has to be committed and visible to other
 * requests while the order is still being created.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    /** Two attempts are enough: the only retry is for a claim that expired between our two calls. */
    private static final int MAX_CLAIM_ATTEMPTS = 2;

    private final IdempotencyRecordStore store;
    private final IdempotencyProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public <T> IdempotentResult<T> execute(IdempotentRequest request, Class<T> responseType, Supplier<T> action) {
        String fingerprint = fingerprint(request.payload());

        for (int attempt = 1; attempt <= MAX_CLAIM_ATTEMPTS; attempt++) {
            Instant now = Instant.now();
            if (store.claim(request.key(), request.requestTarget(), fingerprint, now, properties.getTtl())) {
                return runAndRecord(request, responseType, action);
            }

            Optional<IdempotencyRecord> existing = store.find(request.key());
            if (existing.isEmpty()) {
                continue; // the holder released the key between our insert and our read
            }
            if (existing.get().isExpired(now)) {
                store.release(request.key());
                continue;
            }
            return replay(existing.get(), request, fingerprint, responseType);
        }

        throw new BusinessException(
                "Idempotency-Key " + request.key() + " is contended, retry in a moment",
                HttpStatus.CONFLICT, "IDEMPOTENCY_CONTENDED");
    }

    private <T> IdempotentResult<T> runAndRecord(IdempotentRequest request, Class<T> responseType, Supplier<T> action) {
        try {
            T body = action.get();
            store.complete(request.key(), request.successStatus(), toJson(body));
            log.info("Idempotency key {} completed with status {}", request.key(), request.successStatus());
            return new IdempotentResult<>(body, request.successStatus(), false);
        } catch (RuntimeException ex) {
            // Nothing was created, so the client must be able to retry with the same key.
            store.release(request.key());
            throw ex;
        }
    }

    private <T> IdempotentResult<T> replay(IdempotencyRecord stored, IdempotentRequest request,
                                           String fingerprint, Class<T> responseType) {
        if (!stored.getRequestTarget().equals(request.requestTarget())
                || !stored.getRequestFingerprint().equals(fingerprint)) {
            log.warn("Idempotency key {} reused with a different request", request.key());
            throw new BusinessException(
                    "Idempotency-Key " + request.key() + " was already used with a different request",
                    HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED");
        }
        if (!stored.isCompleted()) {
            throw new BusinessException(
                    "The first request with Idempotency-Key " + request.key() + " is still running",
                    HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS");
        }

        log.info("Replaying stored response for idempotency key {}", request.key());
        return new IdempotentResult<>(fromJson(stored.getResponseBody(), responseType),
                stored.getResponseStatus(), true);
    }

    /** SHA-256 over the serialized request, so a reused key with a changed body is detectable. */
    private String fingerprint(Object payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(objectMapper.writeValueAsBytes(payload)));
        } catch (NoSuchAlgorithmException | JacksonException ex) {
            throw new IllegalStateException("Cannot fingerprint idempotent request", ex);
        }
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Cannot store idempotent response", ex);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json.getBytes(StandardCharsets.UTF_8), type);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read stored idempotent response", ex);
        }
    }
}
