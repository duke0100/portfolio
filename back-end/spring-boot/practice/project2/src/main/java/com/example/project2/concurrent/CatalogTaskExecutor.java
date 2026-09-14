package com.example.project2.concurrent;

import com.example.project2.config.CatalogSnapshotProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#the-virtual-thread-executor
 * (this class is the ExecutorService code sample in that section; it is what
 * {@code CatalogSnapshotServiceImpl} submits its Callables and its Runnable to)
 *
 * <p>The executor is {@code newVirtualThreadPerTaskExecutor()}, so there is no pool and no queue.
 * Every task gets its own virtual thread, which costs a few KB of heap instead of a ~1MB OS stack.
 *
 * <p>That also means nothing pushes back on the caller any more. The two semaphores below put the
 * limit back: {@code admission} caps how many tasks may exist, {@code permits} caps how many may
 * touch the database at once. Keep {@code permits} under Hikari's maximum-pool-size or the tasks
 * just queue on the connection pool instead.
 *
 * <p>The pool is deliberately hidden behind this class instead of being published as an
 * {@code Executor} bean. Any {@code Executor} bean makes Spring Boot's own
 * {@code applicationTaskExecutor} back off, and then Modulith's {@code @ApplicationModuleListener}
 * events would run on this executor too.
 */
@Slf4j
@Component
public class CatalogTaskExecutor implements DisposableBean {

    /** How long a shutdown waits for in-flight tasks before it interrupts them. */
    private static final long SHUTDOWN_WAIT_SECONDS = 10;

    private final ExecutorService executor;
    private final Semaphore admission;
    private final Semaphore permits;
    private final MeterRegistry meterRegistry;
    private final long permitTimeoutNanos;

    public CatalogTaskExecutor(CatalogSnapshotProperties properties, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.permitTimeoutNanos = properties.getPermitTimeout().toNanos();
        // Bounded waiting room. Without it a spike would create virtual threads until the heap ran out.
        this.admission = new Semaphore(properties.getMaxInFlightTasks());
        // Fair, so a task cannot be starved by a steady stream of newer ones under load.
        this.permits = new Semaphore(properties.getMaxConcurrentTasks(), true);

        // One virtual thread per task. Names are not set here; use
        // Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("catalog-task-", 1).factory())
        // when you want a thread dump to say which feature is stuck.
        this.executor = ExecutorServiceMetrics.monitor(
                meterRegistry, Executors.newVirtualThreadPerTaskExecutor(),
                "catalog-tasks", Tags.of("pool", "catalog"));

        // A virtual-thread executor has no pool-size or queue-depth gauge, so the limiter is the
        // thing to watch instead. Zero available permits for long means the database is the wall.
        Gauge.builder("catalog.tasks.permits.available", permits, Semaphore::availablePermits)
                .description("Free slots in the database concurrency gate")
                .register(meterRegistry);
        Gauge.builder("catalog.tasks.admission.available", admission, Semaphore::availablePermits)
                .description("Free slots in the bounded waiting room")
                .register(meterRegistry);
    }

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#callable-fan-out
     * Submits work that has a result. The Future is the only way back to that result, and to the
     * exception the task threw.
     *
     * @throws RejectedExecutionException when the waiting room is full, which the service turns
     *                                    into a 503 instead of letting the queue grow
     */
    public <T> Future<T> submit(Callable<T> task) {
        // Non-blocking on purpose: the request thread must not wait here, only inside the task.
        if (!admission.tryAcquire()) {
            meterRegistry.counter("catalog.tasks.rejected", "reason", "admission").increment();
            throw new RejectedExecutionException("Catalog task rejected: waiting room is full");
        }
        try {
            return executor.submit(gated(task));
        } catch (RuntimeException e) {
            admission.release();
            throw e;
        }
    }

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#runnable-fire-and-forget
     * Runs work that has no result. This is {@code execute}, not {@code submit}, so a thrown
     * exception reaches the thread's uncaught handler instead of sitting in a Future nobody reads.
     *
     * <p>A rejection here is counted and dropped. Telemetry must never fail the request it describes.
     */
    public void execute(Runnable task) {
        if (!admission.tryAcquire()) {
            meterRegistry.counter("catalog.tasks.rejected", "reason", "admission").increment();
            log.warn("Catalog fire-and-forget task dropped: waiting room is full");
            return;
        }
        // Wrapped here, on the caller's thread, so the MDC copy is the caller's and not an empty one.
        Callable<Void> gatedTask = gated(() -> {
            task.run();
            return null;
        });
        try {
            executor.execute(() -> {
                try {
                    gatedTask.call();
                } catch (Exception e) {
                    log.warn("Catalog fire-and-forget task failed", e);
                }
            });
        } catch (RuntimeException e) {
            admission.release();
            throw e;
        }
    }

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#limiting-an-unbounded-executor
     * Wraps a task with the concurrency gate and the caller's logging context.
     *
     * <p>The permit is taken on the virtual thread, not on the caller's thread. A virtual thread
     * parked on a semaphore costs a few KB of heap and no OS thread, so waiting here is cheap.
     *
     * <p>The finally block is what returns both permits. It always runs, because this executor
     * starts a thread per task immediately - there is no queue a task could be cancelled out of.
     */
    private <T> Callable<T> gated(Callable<T> task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            boolean acquired = false;
            try {
                acquired = permits.tryAcquire(permitTimeoutNanos, TimeUnit.NANOSECONDS);
                if (!acquired) {
                    meterRegistry.counter("catalog.tasks.rejected", "reason", "permit-timeout").increment();
                    throw new RejectedExecutionException("Catalog task gave up waiting for a database slot");
                }
                applyContext(context);
                return task.call();
            } finally {
                if (acquired) {
                    permits.release();
                }
                admission.release();
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
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#under-load
     * Graceful shutdown on a rolling deploy: finish what is running, then stop waiting and
     * interrupt. Without this the JVM would exit with tasks half done.
     */
    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
            log.warn("Catalog tasks still running after {}s, interrupting the rest", SHUTDOWN_WAIT_SECONDS);
            executor.shutdownNow();
        }
    }
}
