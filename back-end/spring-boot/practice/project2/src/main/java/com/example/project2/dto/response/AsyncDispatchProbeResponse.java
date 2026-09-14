package com.example.project2.dto.response;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#self-invocation
 * Side-by-side result of calling the same @Async method two ways, so the pitfall is visible in a
 * response body instead of only in a blog post.
 *
 * <p>{@code wrongReturnTypeOutcome} shows what Spring 6 does with an @Async method that returns
 * neither void nor a Future: it refuses the call. Older Spring silently returned null instead.
 */
public record AsyncDispatchProbeResponse(
        String callerThread,
        String correlationId,
        String selfInvocationThread,
        boolean selfInvocationRanAsync,
        String proxiedThread,
        boolean proxiedRanAsync,
        String correlationIdOnPoolThread,
        String wrongReturnTypeOutcome) {
}
