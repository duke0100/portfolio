package com.example.project2.concurrent;

import com.example.project2.config.CatalogSnapshotProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#the-bounded-pool
 * (this class is the ExecutorService code sample in that section; it is what
 * {@code CatalogSnapshotServiceImpl} submits its Callables and its Runnable to)
 *
 * <p>The pool is deliberately hidden behind this class instead of being published as an
 * {@code Executor} bean. Any {@code Executor} bean makes Spring Boot's own
 * {@code applicationTaskExecutor} back off, and then Modulith's {@code @ApplicationModuleListener}
 * events would run on this pool too.
 */
@Slf4j
@Component
public class CatalogTaskExecutor implements DisposableBean {

    /** How long a shutdown waits for in-flight tasks before it interrupts them. */
    private static final long SHUTDOWN_WAIT_SECONDS = 10;

    private final ExecutorService executor;

    public CatalogTaskExecutor(CatalogSnapshotProperties properties, MeterRegistry meterRegistry) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                properties.getPoolSize(),
                properties.getPoolSize(),
                0L, TimeUnit.MILLISECONDS,
                // Bounded queue. An unbounded one would accept work until the heap runs out.
                new ArrayBlockingQueue<>(properties.getQueueCapacity()),
                namedThreadFactory(),
                // Full queue slows the caller down instead of throwing the task away.
                new ThreadPoolExecutor.CallerRunsPolicy());
        this.executor = ExecutorServiceMetrics.monitor(
                meterRegistry, pool, "catalog-tasks", Tags.of("pool", "catalog"));
    }

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#callable-fan-out
     * Submits work that has a result. The Future is the only way back to that result, and to the
     * exception the task threw.
     */
    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(withLoggingContext(task));
    }

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#runnable-fire-and-forget
     * Runs work that has no result. This is {@code execute}, not {@code submit}, so a thrown
     * exception reaches the thread's uncaught handler instead of sitting in a Future nobody reads.
     */
    public void execute(Runnable task) {
        executor.execute(withLoggingContext(task));
    }

    /** Copies the caller's MDC onto the pool thread, so the log lines keep the same trace id. */
    private <T> Callable<T> withLoggingContext(Callable<T> task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            applyContext(context);
            try {
                return task.call();
            } finally {
                MDC.clear();
            }
        };
    }

    /** Same context copy for the no-result case. */
    private Runnable withLoggingContext(Runnable task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            applyContext(context);
            try {
                task.run();
            } finally {
                MDC.clear();
            }
        };
    }

    private void applyContext(Map<String, String> context) {
        MDC.clear();
        if (context != null) {
            MDC.setContextMap(context);
        }
    }

    /**
     * Named threads, so a thread dump says which pool is stuck. The counter is per pool instance,
     * not shared state.
     */
    private static ThreadFactory namedThreadFactory() {
        AtomicInteger sequence = new AtomicInteger(1);
        return runnable -> new Thread(runnable, "catalog-task-" + sequence.getAndIncrement());
    }

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#under-load
     * Graceful shutdown on a rolling deploy: finish what is running, then stop waiting and
     * interrupt. Without this the JVM would exit with tasks half done.
     */
    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
            log.warn("Catalog task pool still busy after {}s, interrupting the rest", SHUTDOWN_WAIT_SECONDS);
            executor.shutdownNow();
        }
    }
}
