package com.example.project2.entity;

/**
 * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#the-store
 * Tells a retry whether the first attempt is still running or already has a response to replay.
 */
public enum IdempotencyStatus {

    /** The first request holds the key and has not answered yet. A retry gets 409. */
    IN_PROGRESS,

    /** The response is stored. Every retry with the same key replays it. */
    COMPLETED
}
