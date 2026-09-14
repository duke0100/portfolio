package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.config.CatalogAsyncProperties;
import com.example.project2.dto.response.AsyncDispatchProbeResponse;
import com.example.project2.service.AsyncDispatchProbeService;
import com.example.project2.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#self-invocation
 * (this class is the self-invocation code sample in that section)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncDispatchProbeServiceImpl implements AsyncDispatchProbeService {

    /**
     * The bean's own proxy, fetched lazily. A plain constructor parameter of this type would be a
     * circular dependency and the context would fail to start.
     */
    private final ObjectProvider<AsyncDispatchProbeService> self;

    private final CatalogAsyncProperties properties;

    @Override
    public AsyncDispatchProbeResponse probe() {
        String callerThread = Thread.currentThread().getName();

        // Broken on purpose: this call never leaves the object, so the proxy never sees it and the
        // method runs right here on the request thread.
        WorkerSnapshot selfInvoked = await(this.snapshot());

        // Fixed: going through the proxy is what hands the work to the pool.
        WorkerSnapshot proxied = await(self.getObject().snapshot());

        // Not void and not a Future: Spring refuses the call instead of running it.
        String wrongReturnTypeOutcome = callWithWrongReturnType();

        return new AsyncDispatchProbeResponse(
                callerThread,
                MDC.get(CorrelationIdFilter.MDC_KEY),
                selfInvoked.threadName(),
                !selfInvoked.threadName().equals(callerThread),
                proxied.threadName(),
                !proxied.threadName().equals(callerThread),
                proxied.correlationId(),
                wrongReturnTypeOutcome);
    }

    @Async("catalogAsyncExecutor")
    @Override
    public CompletableFuture<WorkerSnapshot> snapshot() {
        return CompletableFuture.completedFuture(
                new WorkerSnapshot(Thread.currentThread().getName(), MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    @Async("catalogAsyncExecutor")
    @Override
    public String snapshotWithWrongReturnType() {
        // Never reached: Spring rejects the call before the body runs.
        return Thread.currentThread().getName();
    }

    /**
     * Calls the badly-shaped @Async method and reports what came back.
     *
     * <p>Spring 6 throws IllegalArgumentException here at call time. Older Spring just handed the
     * caller null, which is the version most blog posts still describe.
     */
    private String callWithWrongReturnType() {
        try {
            return "returned " + self.getObject().snapshotWithWrongReturnType();
        } catch (IllegalArgumentException e) {
            return "rejected at call time: " + e.getMessage();
        }
    }

    /**
     * Blocking here is fine because this endpoint exists to compare threads, but the wait is still
     * bounded - a saturated pool answers 503 instead of pinning a Tomcat thread.
     */
    private WorkerSnapshot await(CompletableFuture<WorkerSnapshot> future) {
        try {
            return future.get(properties.getProbeTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new BusinessException("Async pool did not answer within " + properties.getProbeTimeout(),
                    HttpStatus.SERVICE_UNAVAILABLE, "ASYNC_POOL_BUSY");
        } catch (ExecutionException e) {
            throw new BusinessException("Async probe failed: " + e.getCause().getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, "ASYNC_PROBE_FAILED");
        } catch (InterruptedException e) {
            // Restore the flag, or the next blocking call in this thread behaves oddly.
            Thread.currentThread().interrupt();
            throw new BusinessException("Interrupted while waiting for the async probe",
                    HttpStatus.SERVICE_UNAVAILABLE, "ASYNC_PROBE_INTERRUPTED");
        }
    }
}
