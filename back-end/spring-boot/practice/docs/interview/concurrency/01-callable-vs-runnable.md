# What is the difference between Callable and Runnable? When would you prefer one over the other?

Applied in [project2](../../../project2) (Web + JPA + PostgreSQL, Java 25). Each block links its
source file, and each of those files links back here.

| Section | Code in project2 |
|---|---|
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties), [`CatalogSnapshotProperties.java`](../../../project2/src/main/java/com/example/project2/config/CatalogSnapshotProperties.java) |
| [Threads, stacks and the heap](#threads-stacks-and-the-heap) | — (JVM background) |
| [The virtual thread executor](#the-virtual-thread-executor) | [`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java) |
| [Limiting an unbounded executor](#limiting-an-unbounded-executor) | [`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java), [`CatalogSnapshotServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CatalogSnapshotServiceImpl.java) |
| [Callable fan-out](#callable-fan-out) | [`CatalogSnapshotServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CatalogSnapshotServiceImpl.java), [`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java) |
| [Reading the Future](#reading-the-future) | [`CatalogSnapshotServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CatalogSnapshotServiceImpl.java) |
| [Runnable fire and forget](#runnable-fire-and-forget) | [`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java) |
| [Endpoint](#endpoint) | [`CatalogSnapshotController.java`](../../../project2/src/main/java/com/example/project2/controller/CatalogSnapshotController.java) |
| [Executor types](#executor-types) | — (JDK comparison) |
| [Blocking queue types](#blocking-queue-types) | — (JDK comparison) |
| [Under load](#under-load) | [`CatalogSnapshotConcurrencyTest.java`](../../../project2/src/test/java/com/example/project2/service/impl/CatalogSnapshotConcurrencyTest.java) |

## Answer

`Runnable.run()` returns nothing and cannot throw a checked exception. `Callable.call()` returns a
value and can. `submit(Callable<T>)` gives you a `Future<T>`; `execute(Runnable)` gives you nothing.
Use `Callable` when the caller needs the result or the failure, `Runnable` for fire-and-forget work.

Virtual threads changed where you run them, not what they are. Both interfaces are unchanged.

| | `Runnable` | `Callable<T>` |
|---|---|---|
| Method | `void run()` | `T call() throws Exception` |
| Returns a value | no | yes |
| Checked exceptions | no, wrap them yourself | yes, declared on `call()` |
| Since | Java 1.0 | Java 5 |
| `executor.execute(task)` | yes | does not compile |
| `executor.submit(task)` | yes, `Future<?>` resolving to `null` | yes, `Future<T>` |
| A thrown exception goes to | uncaught handler (`execute`), or into the Future nobody reads (`submit`) | `future.get()`, wrapped in `ExecutionException` |

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
catalog.snapshot.max-concurrent-tasks=4
catalog.snapshot.max-in-flight-tasks=64
catalog.snapshot.permit-timeout=PT1S
catalog.snapshot.timeout=PT2S
catalog.snapshot.low-stock-limit=10
```

| Choice | vs the alternative |
|---|---|
| `max-concurrent-tasks=4` (semaphore permits) | vs 32: each running task holds one of the 10 Hikari connections, so extra permits only queue on Hikari |
| `max-in-flight-tasks=64` | vs no cap: the executor creates a virtual thread per task, so a spike creates them until the heap is gone |
| `permit-timeout=PT1S` | vs waiting forever: a task that cannot get a slot in 1s is shed as 503 rather than added to the pile |
| `timeout=PT2S` | vs no timeout: `future.get()` then waits forever on a stuck query |
| `low-stock-limit=10` | vs an uncapped list: one big category would dump thousands of rows |

[`CatalogSnapshotProperties.java`](../../../project2/src/main/java/com/example/project2/config/CatalogSnapshotProperties.java)
— the same values as defaults, so a test that shadows this file still gets a sane limiter:

```java
@Getter
@Setter
@ConfigurationProperties(prefix = "catalog.snapshot")
public class CatalogSnapshotProperties {

    /** Tasks allowed to run at the same time. Each running task holds one database connection. */
    private int maxConcurrentTasks = 4;

    /** Tasks allowed to exist at all, running plus waiting. Over this the submit is rejected. */
    private int maxInFlightTasks = 64;

    /** How long a task waits for its turn before it gives up and reports the service as busy. */
    private Duration permitTimeout = Duration.ofSeconds(1);

    private Duration timeout = Duration.ofSeconds(2);
    private int lowStockLimit = 10;
}
```

[`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties)
— the same knobs set the wrong way on purpose:

```properties
catalog.snapshot.max-concurrent-tasks=32
catalog.snapshot.max-in-flight-tasks=100000
catalog.snapshot.timeout=PT60S
```

## Threads, stacks and the heap

A platform thread is a thin Java wrapper around one OS thread. Your code always ends up on an OS
thread. The only question is how many of them you need to get there.

| | OS thread | Platform thread (`new Thread()`) | Virtual thread (Java 21+) |
|---|---|---|---|
| Created by | the kernel | the JVM asking the kernel | the JVM alone, no kernel call |
| Scheduled by | OS scheduler | OS scheduler | JVM, onto a `ForkJoinPool` of carrier threads |
| Mapping | — | 1:1 with an OS thread | M:N, many virtual threads over few carriers |
| Stack lives | OS memory, fixed ~1MB (`-Xss`) | the same memory, just wrapped | the **heap**, as chunks that grow from a few hundred bytes |
| A blocked thread costs | its full stack | its full 1MB, even while `WAITING` | a few KB of heap, and no OS thread at all |
| Switch cost | ~1-10 µs, kernel mode | ~1-10 µs, kernel mode | ~100 ns, user space |
| Reclaimed by | the OS on exit | the OS on exit | the GC, once the task ends |
| 10,000 idle threads | — | ~10 GB reserved | tens of MB of heap |

What happens on a blocking call, in one line each:

- **Platform thread:** the OS parks it. The 1MB stack stays reserved and the core goes to someone else.
- **Virtual thread:** the JVM copies its stack chunk to the heap, **unmounts** it from the carrier,
  and the carrier picks up another virtual thread. On wake-up the stack is copied back and it
  **mounts** again, possibly onto a different carrier.

Carrier count defaults to the CPU count (`jdk.virtualThreadScheduler.parallelism`). Pinning is when
a virtual thread cannot unmount, which wastes the whole point. One of the three classic causes is
now gone:

| Pinning cause | Status in Java 25 | Effect |
|---|---|---|
| Blocking inside `synchronized` | **fixed** in Java 24 (JEP 491) — it unmounts now | none |
| A native frame on the stack (JNI) | still pins | the carrier is held for the whole call |
| A class-initializer frame | still pins | rare, but it holds the carrier |

Virtual threads do not make anything faster. They remove the cost of *waiting*, so they help
I/O-bound work and do nothing for CPU-bound work or for a 10-connection Hikari pool.

## The virtual thread executor

[`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java):

```java
// One virtual thread per task. Names are not set here; use
// Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("catalog-task-", 1).factory())
// when you want a thread dump to say which feature is stuck.
this.executor = ExecutorServiceMetrics.monitor(
        meterRegistry, Executors.newVirtualThreadPerTaskExecutor(),
        "catalog-tasks", Tags.of("pool", "catalog"));
```

- The bean type is `CatalogTaskExecutor`, not `Executor`. An `Executor` bean would make Boot's
  `applicationTaskExecutor` back off, moving `@Async` work onto this executor.
- `ExecutorServiceMetrics` still times executions, but `executor.queued` and `executor.pool.size`
  are gone. There is no queue and no pool to measure.

What changed and why, against the `ThreadPoolExecutor` this module used before:

| | Before (Java 8-20) | Now (Java 21+) |
|---|---|---|
| Threads | 4 platform threads, reused | one virtual thread per task, discarded after |
| Where work waits | in an `ArrayBlockingQueue(64)` | nowhere — every task starts immediately |
| Backpressure | queue full → `CallerRunsPolicy` | **none by default** — you add it, see below |
| Cost of 1,000 waiting tasks | 996 objects queued, 4 threads | 1,000 virtual threads, a few MB of heap |
| Tuning knob | pool size vs queue capacity | how many may run, how many may exist |

The old knowledge is still what you say in an interview about a pre-21 codebase: a hand-built
`ThreadPoolExecutor` was the right answer because `Executors.newFixedThreadPool(n)` hides an
unbounded `LinkedBlockingQueue`.

<!-- not in this repo -->
```java
// What newFixedThreadPool(n) actually builds - the queue is Integer.MAX_VALUE deep
new ThreadPoolExecutor(n, n, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
```

## Limiting an unbounded executor

`newVirtualThreadPerTaskExecutor()` never rejects and never queues, so "bounded pool + bounded
queue" stops being the limit. Two semaphores put it back.

[`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java):

```java
// Bounded waiting room. Without it a spike would create virtual threads until the heap ran out.
this.admission = new Semaphore(properties.getMaxInFlightTasks());
// Fair, so a task cannot be starved by a steady stream of newer ones under load.
this.permits = new Semaphore(properties.getMaxConcurrentTasks(), true);
```

| Semaphore | Replaces | Acquired | On failure |
|---|---|---|---|
| `admission` (64) | the queue's capacity | `tryAcquire()` on the caller's thread, non-blocking | `RejectedExecutionException` → 503 |
| `permits` (4) | the pool's core size | `tryAcquire(1s)` inside the virtual thread | `RejectedExecutionException` → 503 |

[`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java)
— admission is checked before a thread is ever created:

```java
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
```

[`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java)
— the gate itself runs on the virtual thread, where blocking is cheap:

```java
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
```

- Waiting on the semaphore costs a parked virtual thread, not a parked OS thread. That is why the
  permit is taken here and not on the caller's thread.
- The `finally` always runs, because this executor starts a thread per task at once. There is no
  queue a task could be cancelled out of before it starts, so the permits cannot leak.

[`CatalogSnapshotServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CatalogSnapshotServiceImpl.java)
— shedding load is an answer, so it gets a status code:

```java
private BusinessException busy(RejectedExecutionException cause) {
    meterRegistry.counter("catalog.snapshot.rejected").increment();
    log.warn("Catalog snapshot shed load: {}", cause.getMessage());
    return new BusinessException("Catalog snapshot is busy, retry shortly",
            HttpStatus.SERVICE_UNAVAILABLE, "SNAPSHOT_BUSY");
}
```

Sizing `max-concurrent-tasks` is a budget against Hikari, not a guess:

```text
hikari maximum-pool-size (10)  >=  catalog tasks (4)
                                 + @Async stages that query (3)
                                 + Tomcat threads querying directly (2)
                                 + buffer (1)
```

CPU-bound work wants roughly one slot per core. I/O-bound work wants more, because the slot is
idle most of the time. Start low, watch `hikaricp.connections.pending`, then raise it.

`StructuredTaskScope` is the other limiter, and a better fit when the tasks belong to one request.
It is still a preview API in Java 25 (JEP 505), so it is not in this module — it needs
`--enable-preview`:

<!-- not in this repo -->
```java
try (var scope = StructuredTaskScope.open(
        StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow(),
        cf -> cf.withTimeout(properties.getTimeout()))) {

    var count = scope.fork(() -> productRepository.countByCategory(categoryId));
    var average = scope.fork(() -> productRepository.averagePriceByCategory(categoryId));

    scope.join();                  // one deadline, and one failure cancels the siblings
    return build(count.get(), average.get());
}
```

- It bounds a task's *lifetime*, not the concurrency level. You still need the semaphore to protect
  the connection pool.

Rule of thumb from the old world that survives intact: anything that can grow without a limit —
threads, queue, connection pool, result list — needs one.

## Callable fan-out

[`CatalogSnapshotServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CatalogSnapshotServiceImpl.java)
— three independent queries, so the endpoint costs one query of latency, not three:

```java
Callable<Long> countTask = () -> productRepository.countByCategory(categoryId);
Callable<BigDecimal> averagePriceTask = () -> {
    Double average = productRepository.averagePriceByCategory(categoryId);
    return average == null
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
};
Callable<List<ProductSummaryResponse>> lowStockTask = () ->
        productRepository.findLowStockByCategory(categoryId, PageRequest.ofSize(properties.getLowStockLimit()));

try {
    countFuture = catalogTaskExecutor.submit(countTask);
    averagePriceFuture = catalogTaskExecutor.submit(averagePriceTask);
    lowStockFuture = catalogTaskExecutor.submit(lowStockTask);
} catch (RejectedExecutionException e) {
    throw busy(e);
}
```

- The method is **not** `@Transactional`: a transaction lives in a `ThreadLocal` and never follows a
  task onto another thread. Each task opens its own.
- Virtual threads do not change that. A `ThreadLocal` is per virtual thread, and a virtual thread is
  created fresh for every task.

[`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java)
— capped by the `Pageable`:

```java
@Query("""
        select new com.example.project2.dto.response.ProductSummaryResponse(
            p.id, p.name, p.brand, p.price, p.stockQuantity)
        from Product p
        where p.category.id = :categoryId
          and p.stockQuantity <= p.lowStockThreshold
        order by p.stockQuantity asc, p.id asc
        """)
List<ProductSummaryResponse> findLowStockByCategory(@Param("categoryId") Long categoryId, Pageable pageable);
```

## Reading the Future

[`CatalogSnapshotServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CatalogSnapshotServiceImpl.java)
— one deadline for the whole fan-out, not one per task:

```java
long deadline = System.nanoTime() + properties.getTimeout().toNanos();
Optional<Long> count = await(countFuture, deadline, "productCount");
Optional<BigDecimal> averagePrice = await(averagePriceFuture, deadline, "averagePrice");
Optional<List<ProductSummaryResponse>> lowStock = await(lowStockFuture, deadline, "lowStock");
```

[`CatalogSnapshotServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CatalogSnapshotServiceImpl.java):

```java
private <T> Optional<T> await(Future<T> future, long deadlineNanos, String part) {
    try {
        long remaining = Math.max(0, deadlineNanos - System.nanoTime());
        return Optional.ofNullable(future.get(remaining, TimeUnit.NANOSECONDS));
    } catch (TimeoutException e) {
        future.cancel(true);
        meterRegistry.counter("catalog.snapshot.part.timeout", "part", part).increment();
        log.warn("Catalog snapshot part '{}' missed the {} budget, answering partially",
                part, properties.getTimeout());
        return Optional.empty();
    } catch (ExecutionException e) {
        throw asRuntime(e.getCause(), part);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        future.cancel(true);
        throw new BusinessException("Catalog snapshot was interrupted",
                HttpStatus.SERVICE_UNAVAILABLE, "SNAPSHOT_INTERRUPTED");
    }
}
```

- Unwrap the `ExecutionException`, or `GlobalExceptionHandler` never sees the real cause.
- `cancel(true)` interrupts the task, so no query runs on for a result nobody reads.
- A `RejectedExecutionException` arriving through `ExecutionException` is the permit timeout, which
  `asRuntime` turns into the same 503 as a full waiting room.

## Runnable fire and forget

[`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java):

```java
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
```

[`CatalogSnapshotServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CatalogSnapshotServiceImpl.java):

```java
catalogTaskExecutor.execute(() -> recordSnapshotServed(categoryId));
```

- `execute`, not `submit`: a `submit(Runnable)` failure is parked in a Future nobody reads.
- Telemetry never fails the request it describes, so a rejection here is counted and dropped.
- MDC, `SecurityContext` and the transaction are all `ThreadLocal`. Copy what you need at submit time.

## Endpoint

[`CatalogSnapshotController.java`](../../../project2/src/main/java/com/example/project2/controller/CatalogSnapshotController.java):

```java
@GetMapping("/{categoryId}/snapshot")
public ApiResponse<CatalogSnapshotResponse> snapshot(@PathVariable Long categoryId) {
    return ApiResponse.ok(catalogSnapshotService.snapshot(categoryId));
}
```

`GET /api/v1/catalog/categories/3/snapshot` →

```json
{
  "success": true,
  "status": 200,
  "data": {
    "categoryId": 3,
    "categoryName": "Laptops",
    "productCount": 41237,
    "averagePrice": 918.44,
    "lowStockProducts": [
      { "id": 5512, "name": "ProBook 14", "brand": "Acme", "price": 749.00, "stockQuantity": 1 }
    ],
    "partial": false
  }
}
```

- `partial: true` means a task missed the 2s budget, so a missing number is not read as a zero.
- An unknown category is a 404, raised before any task is submitted. A shed request is a 503 with
  code `SNAPSHOT_BUSY`.

## Executor types

| Executor | Backing threads | Queue | Bounded? | On overload | Use when | What bites you |
|---|---|---|---|---|---|---|
| `new ThreadPoolExecutor(...)` | platform, core → max | whatever you pass | yes, if you pass a bounded queue | your `RejectedExecutionHandler` | you want every knob | you must size four things correctly |
| `newFixedThreadPool(n)` | n platform | `LinkedBlockingQueue` **unbounded** | threads yes, queue no | never rejects, grows | quick jobs, known small load | OOM under a spike, and latency hides in the queue |
| `newCachedThreadPool()` | unbounded platform | `SynchronousQueue` (capacity 0) | **no** | creates another thread | many short bursts, never under load | thousands of OS threads, then `OutOfMemoryError: unable to create native thread` |
| `newSingleThreadExecutor()` | 1 platform | unbounded | no | never rejects | strict ordering, one-at-a-time | one slow task blocks everything behind it |
| `newScheduledThreadPool(n)` | n platform | `DelayedWorkQueue`, unbounded | no | never rejects | `scheduleAtFixedRate`, retries | an uncaught exception silently kills the schedule |
| `ForkJoinPool.commonPool()` | CPU-1 platform, shared | per-thread deques, work-stealing | fixed threads | caller may help run | CPU-bound divide-and-conquer | `parallelStream()` shares it, so one blocking task starves the JVM |
| `newWorkStealingPool()` | a private `ForkJoinPool` | work-stealing deques | fixed threads | caller may help run | CPU-bound, no ordering needed | no FIFO guarantee, no rejection |
| `newVirtualThreadPerTaskExecutor()` | one virtual thread per task | **none** | **no** | always accepts | blocking I/O, high fan-out | no backpressure at all — add a `Semaphore` |
| Spring `ThreadPoolTaskExecutor` | wraps `ThreadPoolExecutor` | `LinkedBlockingQueue(queueCapacity)` | yes, if you set the capacity | your rejection policy | `@Async`, `@Scheduled` | `queueCapacity` defaults to `Integer.MAX_VALUE` |
| Spring `SimpleAsyncTaskExecutor` (virtual) | virtual, via `spring.threads.virtual.enabled=true` | none | `setConcurrencyLimit(n)` only | blocks the caller when limited | Boot 3.2+ `@Async` on virtual threads | unlimited unless you set the concurrency limit |

Picking one:

```text
Blocking I/O, high fan-out      -> newVirtualThreadPerTaskExecutor + Semaphore
Blocking I/O, pre-Java-21       -> ThreadPoolExecutor + ArrayBlockingQueue + CallerRunsPolicy
CPU-bound, splittable           -> ForkJoinPool (a private one, not the common pool)
Periodic work                   -> ScheduledThreadPoolExecutor, guarded so it runs on one instance
Spring @Async                   -> ThreadPoolTaskExecutor, or SimpleAsyncTaskExecutor with a limit
```

## Blocking queue types

Still the right table for a `ThreadPoolExecutor`. A bounded queue is also how you put backpressure
in front of a virtual-thread executor, so it did not stop mattering in Java 21.

| Queue | Bounded | Structure | Locks | Throughput | Memory | Pairs with | Pitfall |
|---|---|---|---|---|---|---|---|
| `ArrayBlockingQueue` | always, fixed at construction | one pre-allocated array | one lock for put and take | good, drops under heavy contention | allocated once, flat | a fixed pool you want capped | size is final; too small rejects, too big hides latency |
| `LinkedBlockingQueue` | optional | node per element | two locks, put and take independent | best with many producers and consumers | one node object per element, more GC | `newFixedThreadPool` | defaults to `Integer.MAX_VALUE` — unbounded unless you say otherwise |
| `SynchronousQueue` | capacity 0 | no storage, direct handoff | handoff, optional fairness | highest, when a consumer is free | none | `newCachedThreadPool` | with a fixed pool every `offer` fails once all threads are busy |
| `PriorityBlockingQueue` | no | binary heap, grows | one lock | fine, `O(log n)` per op | grows without limit | priority work | unbounded, and a low-priority task can starve forever |
| `DelayQueue` | no | heap ordered by delay | one lock | fine | grows without limit | schedulers, retry with backoff | `take()` returns only when the delay expires, so the size can hide a backlog |
| `LinkedTransferQueue` | no | CAS-based, lock-free | none | highest of the linked queues | node per element | handoff plus buffering | unbounded, so it is not a backpressure tool |
| `ConcurrentLinkedQueue` | no | CAS-based, lock-free | none | very high | node per element | not a `BlockingQueue` at all | no `take()`, so it cannot back an executor; `size()` is `O(n)` |

`ArrayBlockingQueue` is usually the right default: you cannot forget to bound it, and one array
means little GC churn.

Rejection policies, once that bounded queue is full:

| Policy | Behaviour | Use when |
|---|---|---|
| `AbortPolicy` (default) | throws `RejectedExecutionException` | the caller can handle a 503 |
| `CallerRunsPolicy` | the submitting thread runs the task itself | you want the producer throttled |
| `DiscardPolicy` | drops the new task silently | the work is truly optional |
| `DiscardOldestPolicy` | drops the oldest queued task, then retries | only the freshest item matters |

The semaphore version of this table is in [Limiting an unbounded executor](#limiting-an-unbounded-executor):
`tryAcquire()` is `AbortPolicy`, `acquire()` is `CallerRunsPolicy`-ish, `tryAcquire(timeout)` is both.

## Comparison

| Aspect | `Runnable` | `Callable<T>` |
|---|---|---|
| What it does | runs work, returns nothing | runs work, returns a value |
| When it applies | telemetry, cache warm-up, cleanup | anything the response needs |
| Failure handling | log it inside the task | `future.get()` rethrows it to the caller |
| Performance at 1M rows | no effect on request latency | 3 sequential queries become 1 round of latency |
| Under concurrent callers | dropped with a warning when the waiting room is full | shed as 503 in the same case |
| With N instances | per-instance limiter and counter | same; the numbers come from the shared database |
| Failure mode | silent failure if you `submit` and drop the Future | a blocked caller, unless `get` has a timeout |
| Use when | nobody waits for the outcome | someone does |

Rule of thumb: if you would end up reading a `Future` anyway, start with `Callable`.

## Under load

| Hazard | With concurrent callers / N instances | What this code does |
|---|---|---|
| Fan-out multiplies connection demand | 24 callers x 3 tasks want 72 of the 10 connections | 4 permits, so at most 4 tasks hold a connection per instance |
| Virtual threads have no queue | a spike creates one thread per task until the heap is gone | `admission` caps in-flight tasks at 64, then rejects |
| Caller holds a connection too | an outer `@Transactional` waits on three tasks while owning a connection, and the pool deadlocks | `snapshot()` is not transactional |
| A task waits forever for a slot | the fan-out piles up behind a slow database | `tryAcquire(1s)` sheds it as 503 `SNAPSHOT_BUSY` |
| One slow query | an untimed `get()` blocks the request thread | shared 2s deadline, `cancel(true)`, `partial: true` |
| Counters are per-instance | `catalog.snapshot.served` counts this instance only | Micrometer counter, summed by the scraper |
| N instances hit one database | 4 permits per instance x 6 instances = 24 concurrent queries | the semaphore is per JVM, so size it against instances x permits, not permits |
| Rolling deploy | tasks die mid-flight | `destroy()` drains for 10s, then interrupts |

[`CatalogSnapshotConcurrencyTest.java`](../../../project2/src/test/java/com/example/project2/service/impl/CatalogSnapshotConcurrencyTest.java)
— 24 callers at once, 72 tasks, 2 permits. Every caller must still get the same complete snapshot:

```java
@SpringBootTest(properties = {
        "catalog.snapshot.max-concurrent-tasks=2",
        "catalog.snapshot.max-in-flight-tasks=128",
        "catalog.snapshot.permit-timeout=PT20S",
        "catalog.snapshot.timeout=PT20S"
})
class CatalogSnapshotConcurrencyTest {

    @Test
    void concurrentCallers_allGetTheSameCompleteSnapshot() throws Exception {
        ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<CatalogSnapshotResponse>> results = new ArrayList<>();
        for (int i = 0; i < CALLERS; i++) {
            results.add(callers.submit(() -> {
                startGun.await(); // every thread waits here, then they all go at once
                return catalogSnapshotService.snapshot(category.getId());
            }));
        }
        startGun.countDown();

        for (Future<CatalogSnapshotResponse> result : results) {
            CatalogSnapshotResponse snapshot = result.get(60, TimeUnit.SECONDS);
            assertThat(snapshot.isPartial()).isFalse();
            assertThat(snapshot.getProductCount()).isEqualTo(PRODUCTS);
        }
    }
}
```

[`CatalogSnapshotConcurrencyTest.java`](../../../project2/src/test/java/com/example/project2/service/impl/CatalogSnapshotConcurrencyTest.java)
— the second test is the one that proves the limiter, not the executor:

```java
@Test
void permitGate_neverLetsMoreTasksRunThanPermits() throws Exception {
    int tasks = 100; // under max-in-flight-tasks=128, so nothing is rejected here
    AtomicInteger inside = new AtomicInteger();
    AtomicInteger peak = new AtomicInteger();

    for (int i = 0; i < tasks; i++) {
        submitted.add(catalogTaskExecutor.submit(() -> {
            int now = inside.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(5);
                return now;
            } finally {
                inside.decrementAndGet();
                finished.countDown();
            }
        }));
    }

    assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
    assertThat(peak.get()).isLessThanOrEqualTo(MAX_CONCURRENT_TASKS);
}
```

- 100 virtual threads start immediately and 98 of them park on the semaphore. The assertion is the
  peak, not a sleep — without the gate it would be 100.

```bash
mvn -pl project2 -am test -Dtest=CatalogSnapshotConcurrencyTest -Dsurefire.failIfNoSpecifiedTests=false
```

Its log shows thread names like `virtual-153`, one per task, instead of a fixed `catalog-task-1/2`.
There is no `executor.queued` gauge any more, so watch the limiter instead:

```bash
curl localhost:9082/actuator/metrics/catalog.tasks.permits.available     # 0 for long = database-bound
curl localhost:9082/actuator/metrics/catalog.tasks.admission.available   # falling = shedding soon
curl localhost:9082/actuator/metrics/catalog.tasks.rejected              # any value = load shed
curl localhost:9082/actuator/metrics/hikaricp.connections.pending        # above 0 = permits too high
curl localhost:9082/actuator/metrics/catalog.snapshot.part.timeout
```

<!-- not in this repo -->
```bash
hey -z 30s -c 200 http://localhost:8088/api/v1/catalog/categories/3/snapshot
```

Expect 503s rather than a heap climb: `catalog.tasks.rejected` rises while p99 stays flat. That is
the limiter working. With no limiter the same run would show memory growing and every caller slow.

The test runs on H2, which answers in milliseconds, so it raises the timeouts and asserts
correctness rather than timing. Postgres with ~1M rows is where the 4 permits actually matter.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| Treating `newVirtualThreadPerTaskExecutor()` as a drop-in for a pool | you deleted your backpressure; the pool size *was* the limit |
| Pooling virtual threads | they are meant to be created and thrown away; a pool re-adds the limit you just removed |
| `submit(runnable)` and dropping the `Future` | the exception is stored in the Future, so the failure is silent |
| `Executors.newFixedThreadPool(n)` | unbounded queue inside; it grows until the heap is gone |
| `future.get()` with no timeout | one stuck query blocks a request thread forever |
| `@Transactional` around the fan-out | the caller holds a connection while blocked on three more |
| Publishing the executor as an `Executor` bean | Boot's `applicationTaskExecutor` backs off and `@Async` work moves onto yours |
| More permits than Hikari connections | the extra tasks only queue on Hikari, and `connections.pending` is where you see it |
| CPU-bound work on virtual threads | no gain; the carrier pool is only as wide as your cores |
| Blocking in a JNI call | the virtual thread pins its carrier, so a few of them can stall the scheduler |
| `ThreadLocal` caches on virtual threads | one per task instead of one per pool thread, so the cache never warms up |
| Assuming `SecurityContext`/MDC is present | they are `ThreadLocal`, so the new thread starts empty |
| Swallowing `InterruptedException` | the shutdown signal is lost and the executor cannot drain |

## Follow-up questions

**Why does `Callable` exist when `Runnable` came first?** `void run()` could not be changed. Java 5
added `Callable<T>` so a task could return a value.

**Do virtual threads change `Callable` or `Runnable`?** No. `Thread.ofVirtual().start(Runnable)` and
`submit(Callable)` take the same two interfaces they always did.

**Is `synchronized` still a problem on virtual threads?** Not since Java 24 (JEP 491) — a virtual
thread blocking inside `synchronized` now unmounts. Native frames still pin.

**Why a `Semaphore` and not a smaller pool?** There is no pool to make smaller. The permit is the
only thing between the request and the 10 connections Hikari owns.

**Where does the semaphore stop working?** At the JVM boundary. Six instances with 4 permits each
are 24 concurrent queries on one database, so size the permits per fleet.

**`Future` vs `CompletableFuture`?** `Future.get()` blocks and cannot be chained;
`CompletableFuture` composes with `thenCombine`/`allOf`. See
[CompletableFuture vs Future](./02-completablefuture-vs-future.md).

**How do you submit many tasks at once?** `invokeAll` returns one `Future` per task. `invokeAny`
returns the first success and cancels the rest. `StructuredTaskScope` is the modern form.

**What about `new Thread(...)`?** It takes a `Runnable` only. `Callable` means nothing outside an
`ExecutorService`.

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am spring-boot:run

curl localhost:8088/api/v1/catalog/categories/3/snapshot        # partial=false, three numbers at once
curl localhost:8088/api/v1/catalog/categories/999999/snapshot   # 404, before any task is submitted
curl localhost:9082/actuator/metrics/catalog.tasks.permits.available
curl localhost:9082/actuator/metrics/catalog.snapshot.served    # per-instance counter
```

A second instance has its own semaphores and its own counter, so the database sees twice the
concurrency:

```bash
mvn -pl project2 -am spring-boot:run "-Dspring-boot.run.arguments=--server.port=8188 --management.server.port=9182"
```

## References

- [Callable](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/Callable.html)
- [Virtual Threads (JEP 444)](https://openjdk.org/jeps/444)
- [Synchronize Virtual Threads without Pinning (JEP 491)](https://openjdk.org/jeps/491)
- [Structured Concurrency (JEP 505, preview in Java 25)](https://openjdk.org/jeps/505)
- [ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Spring Boot task execution](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)
- Related, same folder: [CompletableFuture vs Future](./02-completablefuture-vs-future.md)
- Related: [Actuator](../spring-boot/01-spring-boot-actuator.md), [N+1 queries](../spring-boot/03-n-plus-one-query-problem.md)
