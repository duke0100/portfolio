package com.example.project2.service;

import com.example.project2.dto.response.AsyncDispatchProbeResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#self-invocation
 * A deploy-time smoke check for the async wiring: it calls the same {@code @Async} method through
 * the proxy and around it, and reports which thread each one landed on.
 *
 * <p>Worth having in a real service. If someone drops {@code @EnableAsync} or turns a bean into a
 * final class, every {@code @Async} method quietly goes synchronous and nothing else tells you.
 */
public interface AsyncDispatchProbeService {

    /** Runs the probe and reports both dispatch paths side by side. */
    AsyncDispatchProbeResponse probe();

    /** Correct shape: returns a future, so the result and any failure can reach the caller. */
    CompletableFuture<WorkerSnapshot> snapshot();

    /** Wrong shape on purpose: not void and not a Future, so Spring 6 refuses to call it. */
    String snapshotWithWrongReturnType();

    /** What one thread saw: its own name, and whether the caller's correlation id came with it. */
    record WorkerSnapshot(String threadName, String correlationId) {
    }
}
