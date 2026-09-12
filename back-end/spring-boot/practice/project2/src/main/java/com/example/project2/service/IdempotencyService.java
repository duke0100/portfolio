package com.example.project2.service;

import java.util.function.Supplier;

/**
 * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#claim-run-record
 * Makes a non-idempotent POST safe to retry: the first call runs the action, every later call with
 * the same Idempotency-Key gets the stored response back.
 */
public interface IdempotencyService {

    /**
     * Runs {@code action} at most once per key.
     *
     * @param request      the key, the endpoint it was used against, and the request body
     * @param responseType type the stored JSON response is read back into on a replay
     * @param action       the business call - it runs in its own transaction
     */
    <T> IdempotentResult<T> execute(IdempotentRequest request, Class<T> responseType, Supplier<T> action);

    /**
     * @param key           the client's Idempotency-Key header
     * @param requestTarget method and path, for example {@code POST /api/v1/checkout/orders}
     * @param payload       the request DTO, hashed into the fingerprint
     * @param successStatus status recorded for the first, successful call
     */
    record IdempotentRequest(String key, String requestTarget, Object payload, int successStatus) {
    }

    /**
     * @param body     the response, freshly produced or replayed from the store
     * @param status   the status the first call returned
     * @param replayed false the first time, true for every retry
     */
    record IdempotentResult<T>(T body, int status, boolean replayed) {
    }
}
