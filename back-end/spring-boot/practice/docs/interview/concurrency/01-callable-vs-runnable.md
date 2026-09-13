# What is the difference between Callable and Runnable? When would you prefer one over the other?

Applied in [project2](../../../project2) (Web + JPA + PostgreSQL). Each block links its source
file, and each of those files links back here.

| Section | Code in project2 |
|---|---|
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties), [`CatalogSnapshotProperties.java`](../../../project2/src/main/java/com/example/project2/config/CatalogSnapshotProperties.java) |
| [The bounded pool](#the-bounded-pool) | [`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java) |
| [Callable fan-out](#callable-fan-out) | [`CatalogSnapshotServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CatalogSnapshotServiceImpl.java), [`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java) |
| [Reading the Future](#reading-the-future) | [`CatalogSnapshotServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CatalogSnapshotServiceImpl.java) |
| [Runnable fire and forget](#runnable-fire-and-forget) | [`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java) |
| [Endpoint](#endpoint) | [`CatalogSnapshotController.java`](../../../project2/src/main/java/com/example/project2/controller/CatalogSnapshotController.java) |
| [Under load](#under-load) | [`CatalogSnapshotConcurrencyTest.java`](../../../project2/src/test/java/com/example/project2/service/impl/CatalogSnapshotConcurrencyTest.java) |

## Answer

`Runnable.run()` returns nothing and cannot throw a checked exception. `Callable.call()` returns a
value and can. `submit(Callable<T>)` gives you a `Future<T>`; `execute(Runnable)` gives you nothing.
Use `Callable` when the caller needs the result or the failure, `Runnable` for fire-and-forget work.

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
catalog.snapshot.pool-size=4
catalog.snapshot.queue-capacity=64
catalog.snapshot.timeout=PT2S
catalog.snapshot.low-stock-limit=10
```

| Choice | vs the alternative |
|---|---|
| `pool-size=4` | vs 32: each task holds one of the 10 Hikari connections, so extra threads only queue |
| `queue-capacity=64` (`ArrayBlockingQueue`) | vs an unbounded queue: that one accepts work until the heap is gone |
| `CallerRunsPolicy` | vs `AbortPolicy`: a full queue slows the caller instead of failing the request |
| `timeout=PT2S` | vs no timeout: `future.get()` then waits forever on a stuck query |
| `low-stock-limit=10` | vs an uncapped list: one big category would dump thousands of rows |

[`CatalogSnapshotProperties.java`](../../../project2/src/main/java/com/example/project2/config/CatalogSnapshotProperties.java)
— the same values as defaults, so a test that shadows this file still gets a sane pool:

```java
@Getter
@Setter
@ConfigurationProperties(prefix = "catalog.snapshot")
public class CatalogSnapshotProperties {

    /** Threads in the fan-out pool. Each running task holds one database connection. */
    private int poolSize = 4;
    private int queueCapacity = 64;
    private Duration timeout = Duration.ofSeconds(2);
    private int lowStockLimit = 10;
}
```

[`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties)
— the same knobs set the wrong way on purpose:

```properties
catalog.snapshot.pool-size=32
catalog.snapshot.timeout=PT60S
```

## The bounded pool

[`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java)
— hand-built, because `Executors.newFixedThreadPool` hides an unbounded queue:

```java
public CatalogTaskExecutor(CatalogSnapshotProperties properties, MeterRegistry meterRegistry) {
    ThreadPoolExecutor pool = new ThreadPoolExecutor(
            properties.getPoolSize(),
            properties.getPoolSize(),
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(properties.getQueueCapacity()),
            namedThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy());
    this.executor = ExecutorServiceMetrics.monitor(
            meterRegistry, pool, "catalog-tasks", Tags.of("pool", "catalog"));
}
```

- The bean type is `CatalogTaskExecutor`, not `Executor`. An `Executor` bean would make Boot's
  `applicationTaskExecutor` back off, moving `@Async` work onto this pool.

[`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java):

```java
@Override
public void destroy() throws InterruptedException {
    executor.shutdown();
    if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
        log.warn("Catalog task pool still busy after {}s, interrupting the rest", SHUTDOWN_WAIT_SECONDS);
        executor.shutdownNow();
    }
}
```

- Without this a rolling deploy kills the JVM mid-task.

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

Future<Long> countFuture = catalogTaskExecutor.submit(countTask);
Future<BigDecimal> averagePriceFuture = catalogTaskExecutor.submit(averagePriceTask);
Future<List<ProductSummaryResponse>> lowStockFuture = catalogTaskExecutor.submit(lowStockTask);
```

- The method is **not** `@Transactional`: a transaction lives in a `ThreadLocal` and never follows a
  task onto a pool thread. Each task opens its own.

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

## Runnable fire and forget

[`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java):

```java
public void execute(Runnable task) {
    executor.execute(withLoggingContext(task));
}

/** Copies the caller's MDC onto the pool thread, so the log lines keep the same trace id. */
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
```

[`CatalogSnapshotServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CatalogSnapshotServiceImpl.java):

```java
catalogTaskExecutor.execute(() -> recordSnapshotServed(categoryId));
```

- `execute`, not `submit`: a `submit(Runnable)` failure is parked in a Future nobody reads.
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
- An unknown category is a 404, raised before any task is submitted.

## Comparison

| Aspect | `Runnable` | `Callable<T>` |
|---|---|---|
| What it does | runs work, returns nothing | runs work, returns a value |
| When it applies | telemetry, cache warm-up, cleanup | anything the response needs |
| Failure handling | log it inside the task | `future.get()` rethrows it to the caller |
| Performance at 1M rows | no effect on request latency | 3 sequential queries become 1 round of latency |
| Under concurrent callers | caller runs it inline when the queue is full | same, plus each task holds a connection |
| With N instances | per-instance pool and counter | same; the numbers come from the shared database |
| Failure mode | silent failure if you `submit` and drop the Future | a pinned thread, unless `get` has a timeout |
| Use when | nobody waits for the outcome | someone does |

Rule of thumb: if you would end up reading a `Future` anyway, start with `Callable`.

## Under load

| Hazard | With concurrent callers / N instances | What this code does |
|---|---|---|
| Fan-out multiplies connection demand | 24 callers x 3 tasks want 72 of the 10 connections | 4 pool threads, so at most 4 tasks hold a connection per instance |
| Caller holds a connection too | an outer `@Transactional` waits on three tasks while owning a connection, and the pool deadlocks | `snapshot()` is not transactional |
| Queue fills up | `AbortPolicy` would turn a spike into 500s | `CallerRunsPolicy` degrades to sequential on the caller thread |
| One slow query | an untimed `get()` pins the request thread | shared 2s deadline, `cancel(true)`, `partial: true` |
| Counters are per-instance | `catalog.snapshot.served` counts this instance only | Micrometer counter, summed by the scraper |
| Rolling deploy | tasks die mid-flight | `destroy()` drains for 10s, then interrupts |

[`CatalogSnapshotConcurrencyTest.java`](../../../project2/src/test/java/com/example/project2/service/impl/CatalogSnapshotConcurrencyTest.java)
— 24 callers at once, 72 tasks, a 2-thread pool. Every caller must still get the same complete
snapshot:

```java
@SpringBootTest(properties = {
        "catalog.snapshot.pool-size=2",
        "catalog.snapshot.queue-capacity=8",
        "catalog.snapshot.timeout=PT20S"
})
class CatalogSnapshotConcurrencyTest {

    @Test
    void concurrentCallers_allGetTheSameCompleteSnapshot() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(CALLERS);
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
            assertThat(snapshot.getLowStockProducts()).hasSize(LOW_STOCK_PRODUCTS);
            assertThat(snapshot.getAveragePrice()).isEqualByComparingTo("114.50");
        }
    }
}
```

```bash
mvn -pl project2 -am test -Dtest=CatalogSnapshotConcurrencyTest -Dsurefire.failIfNoSpecifiedTests=false
```

Its log shows `CallerRunsPolicy` firing: some tasks run on `catalog-task-1/2`, the rest inline on
`pool-3-thread-*`. Watch `executor.queued` near capacity (pool-bound) and
`hikaricp.connections.pending` above zero (database-bound):

```bash
curl localhost:9082/actuator/metrics/executor.queued?tag=name:catalog-tasks
curl localhost:9082/actuator/metrics/executor.execution?tag=name:catalog-tasks
curl localhost:9082/actuator/metrics/hikaricp.connections.pending
curl localhost:9082/actuator/metrics/catalog.snapshot.part.timeout
```

<!-- not in this repo -->
```bash
hey -z 30s -c 50 http://localhost:8088/api/v1/catalog/categories/3/snapshot
```

Expect queueing, not errors: `partial` stays false while p99 rises. Any
`catalog.snapshot.part.timeout` means 2s is too tight for the real row count.

The test runs on H2, which answers in milliseconds, so it raises the timeout and asserts
correctness rather than timing.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| `submit(runnable)` and dropping the `Future` | the exception is stored in the Future, so the failure is silent |
| `Executors.newFixedThreadPool(n)` | unbounded queue inside; it grows until the heap is gone |
| `future.get()` with no timeout | one stuck query pins a request thread forever |
| `@Transactional` around the fan-out | the caller holds a connection while blocked on three more |
| Publishing the pool as an `Executor` bean | Boot's `applicationTaskExecutor` backs off and `@Async` work moves onto your pool |
| Pool bigger than the connection pool | the extra threads only queue on Hikari |
| Assuming `SecurityContext`/MDC is present | they are `ThreadLocal`, so the pool thread starts empty |
| Swallowing `InterruptedException` | the shutdown signal is lost and the pool cannot drain |

## Follow-up questions

**Why does `Callable` exist when `Runnable` came first?** `void run()` could not be changed. Java 5
added `Callable<T>` so a task could return a value.

**`Future` vs `CompletableFuture`?** `Future.get()` blocks and cannot be chained;
`CompletableFuture` composes with `thenCombine`/`allOf`. Three blocking JDBC calls joined right
away do not need it.

**How do you submit many tasks at once?** `invokeAll` returns one `Future` per task. `invokeAny`
returns the first success and cancels the rest.

**Would virtual threads remove the pool?** They remove the thread cost, not the 10-connection limit.

**What about `new Thread(...)`?** It takes a `Runnable` only. `Callable` means nothing outside an
`ExecutorService`.

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am spring-boot:run

curl localhost:8088/api/v1/catalog/categories/3/snapshot        # partial=false, three numbers at once
curl localhost:8088/api/v1/catalog/categories/999999/snapshot   # 404, before any task is submitted
curl localhost:9082/actuator/metrics/catalog.snapshot.served    # per-instance counter
```

A second instance has its own pool and its own counter:

```bash
mvn -pl project2 -am spring-boot:run "-Dspring-boot.run.arguments=--server.port=8188 --management.server.port=9182"
```

## References

- [Callable](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/Callable.html)
- [ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Spring Boot task execution](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)
- Related: [Actuator](../spring-boot/01-spring-boot-actuator.md), [N+1 queries](../spring-boot/03-n-plus-one-query-problem.md)
