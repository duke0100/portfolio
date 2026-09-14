# What are the common pitfalls when using @Async in Spring?

Applied in [project2](../../../project2) (Web + JPA + PostgreSQL, the module that already owns the
shared `@Async` pool). Each block links its source; each file links back here.

| Section | Code in project2 |
|---|---|
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties), [`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties), [`CatalogAsyncProperties.java`](../../../project2/src/main/java/com/example/project2/config/CatalogAsyncProperties.java) |
| [Self invocation](#self-invocation) | [`AsyncDispatchProbeServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/AsyncDispatchProbeServiceImpl.java), [`AsyncDispatchProbeService.java`](../../../project2/src/main/java/com/example/project2/service/AsyncDispatchProbeService.java) |
| [Return types](#return-types) | [`AsyncDispatchProbeServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/AsyncDispatchProbeServiceImpl.java) |
| [The price change](#the-price-change) | [`ProductPriceChangeServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPriceChangeServiceImpl.java), [`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java) |
| [After commit listener](#after-commit-listener) | [`ProductPriceAuditListener.java`](../../../project2/src/main/java/com/example/project2/listener/ProductPriceAuditListener.java), [`ProductPriceAuditServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPriceAuditServiceImpl.java) |
| [Exception handling](#exception-handling) | [`CatalogAsyncConfig.java`](../../../project2/src/main/java/com/example/project2/config/CatalogAsyncConfig.java) |
| [Context propagation](#context-propagation) | [`CorrelationIdFilter.java`](../../../project2/src/main/java/com/example/project2/web/CorrelationIdFilter.java), [`ContextPropagatingTaskDecorator.java`](../../../project2/src/main/java/com/example/project2/concurrent/ContextPropagatingTaskDecorator.java) |
| [Endpoints](#endpoints) | [`ProductPriceChangeController.java`](../../../project2/src/main/java/com/example/project2/controller/ProductPriceChangeController.java) |
| [Under load](#under-load) | [`AsyncPriceAuditConcurrencyTest.java`](../../../project2/src/test/java/com/example/project2/service/impl/AsyncPriceAuditConcurrencyTest.java) |

## Answer

`@Async` is a proxy, not a language feature. Anything that gets past the proxy runs on the caller's
thread and nothing tells you.

| Pitfall | Symptom | Fix here |
|---|---|---|
| Self invocation | runs synchronously, no error | [`ObjectProvider<Self>`](#self-invocation) |
| Wrong return type | `IllegalArgumentException` at the call site | [void, `Future`, `CompletableFuture`](#return-types) |
| No executor bean | a brand new thread per call | [`AsyncConfigurer` pool](#config) |
| `void` method throws | total silence | [`AsyncUncaughtExceptionHandler`](#exception-handling) |
| `ThreadLocal` gone | no trace id, no principal | [`TaskDecorator`](#context-propagation) |
| No transaction on the new thread | `LazyInitializationException`, stale reads | [`@TransactionalEventListener(AFTER_COMMIT)`](#after-commit-listener) |
| Missing `@EnableAsync` | everything synchronous | [`CatalogAsyncConfig`](#config) |
| `@Async` used as a job queue | restart loses the task | [a broker or an outbox](#comparison) |

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
catalog.async.audit-history-limit=20
catalog.async.probe-timeout=PT2S

spring.task.execution.thread-name-prefix=boot-task-
spring.task.execution.pool.core-size=4
spring.task.execution.pool.max-size=8
spring.task.execution.pool.queue-capacity=128
spring.task.execution.shutdown.await-termination=true
spring.task.execution.shutdown.await-termination-period=10s

spring.threads.virtual.enabled=false
```

| Choice | vs the alternative |
|---|---|
| `AsyncConfigurer` in [`CatalogAsyncConfig`](../../../project2/src/main/java/com/example/project2/config/CatalogAsyncConfig.java) | vs no executor bean: Spring falls back to `SimpleAsyncTaskExecutor`, a fresh thread per call that is never reused |
| `queue-capacity=128` | vs Boot's default `Integer.MAX_VALUE`: overload becomes an `OutOfMemoryError` instead of a rejection |
| `CallerRunsPolicy` | vs `AbortPolicy`: the submitter runs the task, so a spike slows callers down instead of dropping audit rows |
| `await-termination=true` | vs the default: in-flight tasks die at SIGTERM on every rolling deploy |
| `core-size=4`, `max-size=8` | the pool only grows past core once the **queue is full**, so with 128 slots thread 5 starts on task 133 |
| `pool-size=4` for `catalogAsyncExecutor` | vs 64: there are 10 Hikari connections, so more threads only queue on the pool |
| `spring.threads.virtual.enabled=false` | vs true: Tomcat switches over, `@Async` does not, because the explicit `AsyncConfigurer` wins — see [virtual threads](./01-callable-vs-runnable.md#the-virtual-thread-executor) |

[`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties)
— the same knobs set the wrong way on purpose:

```properties
catalog.async.probe-timeout=PT0.01S
spring.threads.virtual.enabled=true
```

## Self invocation

[`AsyncDispatchProbeServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/AsyncDispatchProbeServiceImpl.java)
— the same method called both ways, so the difference shows up in a response body:

```java
private final ObjectProvider<AsyncDispatchProbeService> self;

@Override
public AsyncDispatchProbeResponse probe() {
    String callerThread = Thread.currentThread().getName();

    // Broken on purpose: this call never leaves the object, so the proxy never sees it and the
    // method runs right here on the request thread.
    WorkerSnapshot selfInvoked = await(this.snapshot());

    // Fixed: going through the proxy is what hands the work to the pool.
    WorkerSnapshot proxied = await(self.getObject().snapshot());
```

- `ObjectProvider` resolves late, so the self-reference is not a circular dependency at startup.

| Fix | Cost |
|---|---|
| Move the method to another bean | cleanest, and usually the honest design |
| `ObjectProvider<Self>` (used here) | one field, no flags, works with either proxy type |
| `@Lazy` self-injection | a real circular reference, which Boot rejects unless you allow it |
| `AopContext.currentProxy()` + `exposeProxy = true` | your business code now imports Spring AOP, and it needs CGLIB |
| AspectJ weaving | self-calls work, but you take on an agent and a build change |

| | JDK dynamic proxy | CGLIB |
|---|---|---|
| Needs | an interface | nothing |
| Builds | an implementation of the interface | a subclass of your class |
| Cannot advise | methods not on the interface | `final` class, `final`/`private`/`static` methods |
| Default in Boot | no | yes (`proxyTargetClass=true`) |

Boot proxies by class, so `@Async` on a `final` or `private` method does nothing at all.

## Return types

[`AsyncDispatchProbeServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/AsyncDispatchProbeServiceImpl.java)
— the shape that does not work:

```java
@Async("catalogAsyncExecutor")
@Override
public String snapshotWithWrongReturnType() {
    // Never reached: Spring rejects the call before the body runs.
    return Thread.currentThread().getName();
}
```

- Spring 6 throws `IllegalArgumentException: Invalid return type for async method (only Future and void supported)`. Older Spring returned `null` instead.

| Return type | What the caller gets |
|---|---|
| `void` | control back at once; failures go to `AsyncUncaughtExceptionHandler` |
| `Future<T>` | a handle, and the exception only on `get()` |
| `CompletableFuture<T>` | a handle you can chain — see [CompletableFuture](./02-completablefuture-vs-future.md#exception-handling) |
| `ListenableFuture<T>` | removed in Spring 6 |
| anything else | `IllegalArgumentException` at the call site |

## The price change

[`ProductPriceChangeServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPriceChangeServiceImpl.java):

```java
@Override
@Transactional
public ProductPriceChangeResponse changePrice(Long productId, ChangeProductPriceRequest request) {
    Product product = productRepository.findById(productId)
            .orElseThrow(() -> BusinessException.notFound("Product " + productId + " was not found"));

    // The read above cannot decide the race, only the WHERE clause in this UPDATE can.
    int updated = productRepository.updatePriceIfUnchanged(
            productId, request.expectedPrice(), request.newPrice(), request.changedBy(), LocalDateTime.now());

    if (updated == 0) {
        throw BusinessException.conflict(
                "Price of product %d is no longer %s - re-read it and try again"
                        .formatted(productId, request.expectedPrice()));
    }

    // Published inside the transaction, but the listener only runs if this commits.
    events.publishEvent(new ProductPriceChanged(
            productId, request.expectedPrice(), request.newPrice(), request.changedBy()));
```

[`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java)
— the compare-and-set that picks the winner:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
        update Product p
           set p.price = :newPrice,
               p.updatedAt = :changedAt,
               p.updatedBy = :changedBy
         where p.id = :productId
           and p.price = :expectedPrice
        """)
int updatePriceIfUnchanged(@Param("productId") Long productId, ...);
```

- The event carries values, never the `Product`. An entity is detached on another thread, so a lazy field on it throws.
- A bulk update skips `@PreUpdate`, so `updatedAt` is set by hand.

## After commit listener

[`ProductPriceAuditListener.java`](../../../project2/src/main/java/com/example/project2/listener/ProductPriceAuditListener.java):

```java
@Async("catalogAsyncExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onPriceChanged(ProductPriceChanged event) {
    auditService.record(event);
}
```

- A plain `@EventListener` fires while the caller is still uncommitted, so the pool thread reads the *old* price - or audits a change that then rolls back.
- The async hop and the transactional hop sit in two beans, so neither annotation is ever on a self-call.

[`ProductPriceAuditServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPriceAuditServiceImpl.java):

```java
@Override
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void record(ProductPriceChanged event) {
    ProductPriceAudit audit = ProductPriceAudit.builder()
            .productId(event.productId())
            // Present only because the TaskDecorator copied MDC onto this pool thread.
            .correlationId(MDC.get(CorrelationIdFilter.MDC_KEY))
            .workerThread(Thread.currentThread().getName())
            .recordedAt(Instant.now())
            .build();
    auditRepository.save(audit);
```

- After commit the thread has no transaction at all, so this write opens its own.

## Exception handling

[`CatalogAsyncConfig.java`](../../../project2/src/main/java/com/example/project2/config/CatalogAsyncConfig.java):

```java
@Override
public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (throwable, method, params) ->
            log.error("Async void method {} failed with args {}", method, Arrays.toString(params), throwable);
}
```

- Without it a failing `@Async void` method leaves no trace anywhere.
- It never fires for a future-returning method - that failure waits inside the future until someone calls `get()`.

## Context propagation

[`CorrelationIdFilter.java`](../../../project2/src/main/java/com/example/project2/web/CorrelationIdFilter.java)
puts the id in MDC;
[`ContextPropagatingTaskDecorator.java`](../../../project2/src/main/java/com/example/project2/concurrent/ContextPropagatingTaskDecorator.java)
carries it onto the pool:

```java
@Override
public Runnable decorate(Runnable runnable) {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    return () -> {
        applyMdc(contextMap);
        try {
            runnable.run();
        } finally {
            MDC.clear();
            RequestContextHolder.resetRequestAttributes();
        }
    };
}
```

- Pool threads are reused, so skipping the clear would tag the next task with this request's id.

| Context | Why it disappears | Carry it with |
|---|---|---|
| MDC / trace id | `ThreadLocal` in the logging backend | the `TaskDecorator` above |
| `SecurityContextHolder` | `ThreadLocal` per thread | `DelegatingSecurityContextAsyncTaskExecutor`; `MODE_INHERITABLETHREADLOCAL` only covers threads created now, so it is useless with a pool |
| `RequestContextHolder`, request-scoped beans | tied to the servlet thread | the `TaskDecorator`, but only while the request is open |
| Micrometer tracing span | `ThreadLocal` observation scope | `ContextSnapshotFactory` from micrometer-context-propagation |

## Endpoints

[`ProductPriceChangeController.java`](../../../project2/src/main/java/com/example/project2/controller/ProductPriceChangeController.java)

`POST /api/v1/catalog/products/42/price-changes`

```json
{ "expectedPrice": 100.00, "newPrice": 119.99, "changedBy": "pricing-team" }
```

→ answers as soon as the change commits; the audit row lands a few ms later:

```json
{
  "success": true,
  "data": {
    "productId": 42, "productName": "Wireless keyboard",
    "previousPrice": 100.00, "newPrice": 119.99,
    "changedBy": "pricing-team", "auditStatus": "SCHEDULED"
  }
}
```

A second caller who also read `100.00` gets `409` through `GlobalExceptionHandler`:

```json
{ "status": 409, "errorCode": "CONFLICT",
  "message": "Price of product 42 is no longer 100.00 - re-read it and try again" }
```

`GET /api/v1/catalog/products/42/price-changes` → the trail, newest first, capped at 20:

```json
[ { "id": 7, "productId": 42, "oldPrice": 100.00, "newPrice": 119.99,
    "changedBy": "pricing-team", "correlationId": "demo-1",
    "workerThread": "catalog-async-1", "recordedAt": "2026-09-14T09:15:02.441Z" } ]
```

`GET /api/v1/catalog/async/dispatch-probe` → the pitfall, visible at runtime:

```json
{
  "callerThread": "http-nio-8088-exec-1",
  "correlationId": "0f3c",
  "selfInvocationThread": "http-nio-8088-exec-1", "selfInvocationRanAsync": false,
  "proxiedThread": "catalog-async-1", "proxiedRanAsync": true,
  "correlationIdOnPoolThread": "0f3c",
  "wrongReturnTypeOutcome": "rejected at call time: Invalid return type for async method (only Future and void supported): class java.lang.String"
}
```

## Comparison

| Aspect | `@Async` | `AFTER_COMMIT` + `@Async` | Queue / outbox |
|---|---|---|---|
| What it does | runs the method on a pool | runs it once the data is durable | hands the work to a broker |
| When it applies | fire-and-forget, no database dependency | follow-up work that must not see uncommitted data | work that must survive a crash |
| Performance at 1M rows | caller returns at once | same, plus one short extra transaction | one extra network hop |
| Under concurrent callers | pool threads compete for connections | same, and only commits create work | the broker absorbs the spike |
| With N instances | every instance has its own pool, no coordination | same; the publishing instance does the work | any consumer can pick it up |
| Failure mode | task lost on restart, silently | task lost on restart, but never runs on rolled-back data | redelivery, at-least-once |
| Use when | losing the task costs a log line | correctness depends on the commit | losing it is unacceptable |

Once someone says "we must never lose this", use a broker, an outbox, or Modulith's
persistent events — see [modular monolith](../architecture/01-spring-modulith-modular-monolith.md#event-driven-async).

## Under load

| Hazard | With concurrent callers / N instances | What this code does |
|---|---|---|
| Two editors change one price | both read 100.00, both write, one overwrites the other | the CAS `UPDATE` matches on the old price, the loser gets 409 |
| Audit fires before commit | it reads the old price, or audits a rollback | `AFTER_COMMIT`: no commit, no row |
| Queue grows unbounded | tasks pile up until the heap goes | `queue-capacity=128` and `CallerRunsPolicy` |
| Pool wider than the connection pool | tasks hold threads waiting on Hikari | `pool-size=4` against 10 connections |
| Instance dies mid-task | the audit row is simply lost | accepted here; anything critical needs a broker |
| Each instance runs its own pool | an in-JVM counter or cache is wrong on instance two | all state is in Postgres, metrics are Micrometer counters |

[`AsyncPriceAuditConcurrencyTest.java`](../../../project2/src/test/java/com/example/project2/service/impl/AsyncPriceAuditConcurrencyTest.java)
— 16 editors race one price; one wins, one audit row appears:

```java
startGun.countDown();

assertThat(winners).isEqualTo(1);
assertThat(conflicts).hasValue(EDITORS - 1);

// The row lands a moment after the response, which is exactly what @Async bought us.
await().atMost(AUDIT_TIMEOUT).until(() -> auditRepository.countByProductId(productId) == 1);

ProductPriceAuditResponse audit = auditService.recentChanges(productId).getFirst();
// Proof the listener really ran on the pool and not on the caller's thread.
assertThat(audit.workerThread()).startsWith("catalog-async-");
```

The rollback test waits for no sleep. It changes a second product, and once that
row exists the listener has had its turn:

```java
priceChangeService.changePrice(otherProductId, ...);
await().atMost(AUDIT_TIMEOUT).until(() -> auditRepository.countByProductId(otherProductId) == 1);
assertThat(auditRepository.countByProductId(productId)).isZero();
```

```bash
mvn -pl project2 -am test -Dtest=AsyncPriceAuditConcurrencyTest
```

Watch the pool while it runs - a queue that never drains is the bad number:

```bash
curl localhost:9082/actuator/metrics/executor.queued          # tag pool=catalog-async
curl localhost:9082/actuator/metrics/executor.active
curl localhost:9082/actuator/metrics/hikaricp.connections.pending
```

H2 commits far faster than Postgres, so the test proves counts and ordering, never timing.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| Calling an `@Async` method from the same bean | runs on the caller's thread, no error, no log line |
| `@Async` on a `private`, `static` or `final` method | the proxy cannot override it, so it is ignored |
| Forgetting `@EnableAsync` | every `@Async` in the app becomes a comment |
| `@Async` on `@PostConstruct` | the bean is not proxied yet, so it runs synchronously |
| An `@Async` bean injected into a `BeanPostProcessor` | "not eligible for auto-proxying" at startup, and the advice is dropped |
| Expecting the caller's transaction | the new thread has none, entities are detached, lazy fields throw |
| Returning a plain object | `IllegalArgumentException` at the call site on Spring 6 |
| Default `SimpleAsyncTaskExecutor` | a new thread per call, no reuse, no ceiling |
| Unbounded queue | overload turns into `OutOfMemoryError` instead of a rejection |
| `Thread.sleep` in an async test | flaky on a loaded CI box; use Awaitility or Mockito `timeout()` |
| `@Async` as a job queue | per-JVM, no retry, no durability, no redelivery |

## Follow-up questions

**Which executor runs `@Async` if I define none?** `SimpleAsyncTaskExecutor` - a new thread per
call. Boot's `applicationTaskExecutor` takes over if it exists, and
an explicit `AsyncConfigurer` beats both.

**Why does my pool never reach `max-size`?** Threads past the core size only start once the queue
is full.

**How do I choose the pool per method?** `@Async("catalogAsyncExecutor")` names a bean. Slow
outbound work gets its own pool so it cannot starve the rest.

**Can two `@Async` methods share a transaction?** No. Each opens its own on its own thread.

**How do I make an async test deterministic?** Assert the invariant with Awaitility, or register a
synchronous executor in a `@TestConfiguration`.

**What about virtual threads?** `spring.threads.virtual.enabled=true` only swaps Boot's own
executor — see [virtual threads](./01-callable-vs-runnable.md#the-virtual-thread-executor).

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am spring-boot:run

curl -s localhost:8088/api/v1/catalog/async/dispatch-probe | jq   # selfInvocationRanAsync is false

curl -s -X POST localhost:8088/api/v1/catalog/products/42/price-changes \
  -H 'Content-Type: application/json' -H 'X-Correlation-Id: demo-1' \
  -d '{"expectedPrice":100.00,"newPrice":119.99,"changedBy":"pricing-team"}'

curl -s localhost:8088/api/v1/catalog/products/42/price-changes | jq   # workerThread: catalog-async-N
```



```bash
mvn -pl project2 -am spring-boot:run -Dspring-boot.run.arguments=--server.port=8188
```

Each instance has its own pool and its own `catalog-async-1`. The instance that serves the POST writes
the audit row, and if it dies the other never picks the work up.

## References

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [Spring Boot — Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)
- Related, same topic folder: [Callable vs Runnable](./01-callable-vs-runnable.md), [CompletableFuture vs Future](./02-completablefuture-vs-future.md)
- Related, other topic folder: [Spring Modulith](../architecture/01-spring-modulith-modular-monolith.md)
