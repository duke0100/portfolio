# Explain CompletableFuture and how it differs from Future. Give a chaining example.

Applied in [project2](../../../project2) (Web + JPA + PostgreSQL, the module with the ~1M-row
catalog). Each block links its source file, and each of those files links back here.

| Section | Code in project2 |
|---|---|
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties), [`CatalogAsyncProperties.java`](../../../project2/src/main/java/com/example/project2/config/CatalogAsyncProperties.java) |
| [The pipeline](#the-pipeline) | [`ProductPricingServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPricingServiceImpl.java), [`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java) |
| [Which thread runs which stage](#which-thread-runs-which-stage) | [`CatalogAsyncConfig.java`](../../../project2/src/main/java/com/example/project2/config/CatalogAsyncConfig.java) |
| [Timeouts and fallback](#timeouts-and-fallback) | [`ShippingQuoteClient.java`](../../../project2/src/main/java/com/example/project2/client/ShippingQuoteClient.java) |
| [Exception handling](#exception-handling) | [`ProductPricingServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPricingServiceImpl.java) |
| [Fan-in with allOf](#fan-in-with-allof) | [`ProductPricingServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPricingServiceImpl.java) |
| [Spring integration](#spring-integration) | [`CatalogAsyncConfig.java`](../../../project2/src/main/java/com/example/project2/config/CatalogAsyncConfig.java), [`ContextPropagatingTaskDecorator.java`](../../../project2/src/main/java/com/example/project2/concurrent/ContextPropagatingTaskDecorator.java), [`ShippingQuoteClient.java`](../../../project2/src/main/java/com/example/project2/client/ShippingQuoteClient.java) |
| [Endpoints](#endpoints) | [`ProductPricingController.java`](../../../project2/src/main/java/com/example/project2/controller/ProductPricingController.java) |
| [Under load](#under-load) | [`ProductPricingConcurrencyTest.java`](../../../project2/src/test/java/com/example/project2/service/impl/ProductPricingConcurrencyTest.java) |

## Answer

`Future` is a handle you can only poll or block on. `future.get()` parks the calling thread. There
is no callback, no chaining, and no way to complete it yourself.

`CompletableFuture` implements `CompletionStage`: you say what happens *next* instead of waiting.
It also adds failure handling, timeouts, and manual completion — which is how you wrap a
callback-based client.

| | `Future` (Java 5) | `CompletableFuture` (Java 8) |
|---|---|---|
| Get the result | `get()` / `get(timeout)`, both block | `join()`/`get()` block, or a callback that does not |
| Chaining | none, you block and call the next thing | `thenApply`, `thenCompose`, `thenCombine`, `allOf`, `anyOf` |
| Failure | `ExecutionException` out of `get()` | `exceptionally`, `handle`, `whenComplete`, or wrapped in `CompletionException` |
| Timeout | only on `get(timeout)`, per call | `orTimeout`, `completeOnTimeout`, on the stage itself |
| Complete it yourself | no | `complete`, `completeExceptionally`, `obtrudeValue` |
| Cancellation | `cancel(true)` may interrupt the running task | `cancel(true)` never interrupts, it only fails the stage |
| Who runs the work | whoever you submitted to | the executor you pass, or `ForkJoinPool.commonPool()` |

| Method | Function shape | Use when |
|---|---|---|
| `thenApply` | `T -> U` | the next step is a plain transformation |
| `thenCompose` | `T -> CompletionStage<U>` | the next step is itself async — flattens, like `flatMap` |
| `thenCombine` | `(T, U) -> V` | two independent stages, merge both results |
| `thenAccept` / `thenRun` | `T -> void` / `() -> void` | side effect at the end of a chain |
| `allOf` | `CompletableFuture<Void>` | wait for a fan-out, then `join()` each part |
| `anyOf` | first to finish, any type | hedged requests, race two replicas |

`thenApply` over a function that returns a future gives you
`CompletableFuture<CompletableFuture<U>>`, and unwrapping that needs a blocking `join()` inside the
chain. `thenCompose` flattens it instead.

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
catalog.async.pool-size=4
catalog.async.queue-capacity=128
catalog.async.timeout=PT3S
catalog.async.related-limit=5
catalog.async.max-batch-size=20
catalog.async.shipping.base-url=http://localhost:9099
catalog.async.shipping.connect-timeout=500ms
catalog.async.shipping.read-timeout=800ms
catalog.async.shipping.quote-timeout=PT1S
catalog.async.shipping.fallback-rate-per-kg=2.50
catalog.async.shipping.fallback-minimum=4.90
```

| Choice | vs the alternative |
|---|---|
| `pool-size=4` | vs `commonPool` (cores − 1, shared with every parallel stream): a blocking query there stalls unrelated work |
| `queue-capacity=128` + `CallerRunsPolicy` | vs an unbounded queue: that one accepts stages until the heap is gone |
| `timeout=PT3S` via `orTimeout` | vs no timeout: the servlet thread is free either way, but the client waits forever |
| `quote-timeout=PT1S` | vs relying on the socket timeouts alone: a partner that trickles bytes resets the read timeout on every byte |
| `connect/read-timeout` both set | vs Spring's default of none: one hung partner holds a pool thread until it gives up |
| `max-batch-size=20` | vs unbounded `ids`: each id costs 5 stages and 4 round trips |

[`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties)
— the same knobs set the wrong way on purpose:

```properties
catalog.async.pool-size=64
catalog.async.shipping.read-timeout=5m
catalog.async.max-batch-size=500
```

## The pipeline

[`ProductPricingServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPricingServiceImpl.java)
— five stages: product, category count, category average, related products, shipping quote:

```java
long startedAt = System.nanoTime();

// Stage 1. supplyAsync starts the chain on our pool, not on the request thread.
CompletableFuture<PricedProductResponse> productFuture =
        CompletableFuture.supplyAsync(() -> loadProduct(productId), catalogAsyncExecutor);

// Stages 2+3. thenCompose, because categoryStats returns a future of its own. thenApply
// here would give CompletableFuture<CompletableFuture<CategoryPriceStatsResponse>>.
CompletableFuture<CategoryPriceStatsResponse> statsFuture =
        productFuture.thenCompose(this::categoryStats);

// Stage 4. thenApplyAsync, not thenApply: this one runs a query and must not run inline on
// whichever thread happened to complete stage 1.
CompletableFuture<List<ProductSummaryResponse>> relatedFuture =
        productFuture.thenApplyAsync(this::relatedProducts, catalogAsyncExecutor);

// Stage 5. Independent of stages 2 to 4, so it runs at the same time as them.
CompletableFuture<ShippingQuoteResponse> shippingFuture =
        productFuture.thenCompose(this::shippingQuote);
```

- Stages 2–5 hang off `productFuture`, so they start together. Latency is the slowest branch, not
  the sum of five.
- Not `@Transactional`: a transaction is a ThreadLocal and never reaches a pool thread. Each stage
  opens its own.

[`ProductPricingServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPricingServiceImpl.java)
— the stage that makes `thenCompose` necessary:

```java
private CompletableFuture<CategoryPriceStatsResponse> categoryStats(PricedProductResponse product) {
    Long categoryId = product.getCategoryId();
    if (categoryId == null) {
        // Nothing to query. completedFuture keeps the type of the chain without touching the pool.
        return CompletableFuture.completedFuture(CategoryPriceStatsResponse.builder()
                .productCount(0L)
                .build());
    }
    CompletableFuture<Long> countFuture = CompletableFuture.supplyAsync(
            () -> productRepository.countByCategory(categoryId), catalogAsyncExecutor);
    CompletableFuture<BigDecimal> averageFuture = CompletableFuture.supplyAsync(
            () -> averagePrice(categoryId), catalogAsyncExecutor);

    return countFuture.thenCombine(averageFuture, (count, average) -> CategoryPriceStatsResponse.builder()
            .categoryId(categoryId)
            .productCount(count)
            .averagePrice(average)
            .priceVsAveragePercent(priceVsAverage(product.getPrice(), average))
            .build());
}
```

- `thenCombine` merges two branches that never wait for each other. Chaining them would serialise
  two queries that are independent.

[`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java)
— stage 1 projects six columns, and `p.category.id` reads the FK without joining:

```java
@Query("""
        select new com.example.project2.dto.response.PricedProductResponse(
            p.id, p.name, p.brand, p.price, p.weight, p.category.id)
        from Product p
        where p.id = :productId
        """)
Optional<PricedProductResponse> findPricingProjection(@Param("productId") Long productId);
```

## Which thread runs which stage

| Call | Runs on |
|---|---|
| `supplyAsync(fn)` | `ForkJoinPool.commonPool()` — shared with every parallel stream in the JVM |
| `supplyAsync(fn, executor)` | your executor. This is the only form to use for blocking work |
| `thenApply(fn)` | the thread that completed the previous stage, or the caller if it is already done |
| `thenApplyAsync(fn)` | `commonPool` again |
| `thenApplyAsync(fn, executor)` | your executor |

- `commonPool` is not yours: a JDBC call parked there blocks work you never wrote.
- "Whichever thread is free" is fine for a `map` and wrong for a query. That is why stage 4 is
  `thenApplyAsync`.

## Timeouts and fallback

[`ShippingQuoteClient.java`](../../../project2/src/main/java/com/example/project2/client/ShippingQuoteClient.java)
— the outbound call. Both socket timeouts are mandatory:

```java
public ShippingQuoteClient(CatalogAsyncProperties properties, MeterRegistry meterRegistry) {
    this.shipping = properties.getShipping();
    this.meterRegistry = meterRegistry;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    // Both timeouts are mandatory. Without them one hung partner holds a pool thread forever.
    factory.setConnectTimeout(shipping.getConnectTimeout());
    factory.setReadTimeout(shipping.getReadTimeout());
    this.restClient = RestClient.builder()
            .requestFactory(factory)
            .baseUrl(shipping.getBaseUrl())
            .build();
}
```

[`ProductPricingServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPricingServiceImpl.java)
— the stage budget and the recovery:

```java
private CompletableFuture<ShippingQuoteResponse> shippingQuote(PricedProductResponse product) {
    return shippingQuoteClient.quoteAsync(product.getId(), product.getWeight())
            .orTimeout(properties.getShipping().getQuoteTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .exceptionally(throwable -> {
                Throwable cause = rootCause(throwable);
                log.warn("Shipping partner unavailable for product {} ({}: {}), using the flat rate",
                        product.getId(), cause.getClass().getSimpleName(), cause.getMessage());
                return shippingQuoteClient.fallbackQuote(product.getWeight());
            });
}
```

| Choice | vs the alternative |
|---|---|
| `orTimeout` + `exceptionally` | vs `completeOnTimeout(fallbackQuote(...), ...)`: that argument is built eagerly on every call, even when the partner answers, so the FALLBACK counter would lie |
| Fallback marked `"source": "FALLBACK"` | vs silently returning an estimate: the caller cannot tell a real price from a guess |
| Timeout fails the stage only | neither `orTimeout` nor `cancel` stops the HTTP call. The thread is still busy until the socket timeout fires |

## Exception handling

[`ProductPricingServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPricingServiceImpl.java):

```java
private ProductPricingResponse completeOrTranslate(ProductPricingResponse response, Throwable throwable) {
    if (throwable == null) {
        return response;
    }
    throw translate(throwable);
}

private Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    while ((current instanceof CompletionException || current instanceof ExecutionException)
            && current.getCause() != null) {
        current = current.getCause();
    }
    return current;
}
```

- Every stage wraps its failure in `CompletionException`, and nesting wraps it again. Spring MVC
  unwraps exactly one level, so the chain must end with `CompletionException(BusinessException)` or
  a missing product answers 500 instead of 404.

| Operator | Signature | Recovers? |
|---|---|---|
| `exceptionally` | `Throwable -> T` | yes, failure only |
| `handle` | `(T, Throwable) -> U` | yes, both outcomes, may change the type |
| `whenComplete` | `(T, Throwable) -> void` | no — logging and cleanup, the failure passes through |

## Fan-in with allOf

[`ProductPricingServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPricingServiceImpl.java)
— `allOf` returns `Void`, so you `join()` each part inside the `thenApply`:

```java
return CompletableFuture.allOf(statsFuture, relatedFuture, shippingFuture)
        .thenApply(ignored -> {
            // join() here cannot block: allOf only completes once all three are done.
            ShippingQuoteResponse shipping = shippingFuture.join();
            return ProductPricingResponse.builder()
                    .product(productFuture.join())
                    .categoryStats(statsFuture.join())
                    .relatedProducts(relatedFuture.join())
                    .shipping(shipping)
                    .degraded("FALLBACK".equals(shipping.getSource()))
                    .elapsedMs(elapsedMs(startedAt))
                    .build();
        })
        .orTimeout(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
        .handle(this::completeOrTranslate);
```

[`ProductPricingServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductPricingServiceImpl.java)
— the batch call recovers each id *before* the fan-in:

```java
List<CompletableFuture<ProductPricingBatchItemResponse>> itemFutures = ids.stream()
        .map(id -> pricing(id).handle((pricing, throwable) -> batchItem(id, pricing, throwable)))
        .toList();

return CompletableFuture.allOf(itemFutures.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> itemFutures.stream()
                .map(CompletableFuture::join)
                .toList());
```

- One failing stage fails `allOf`, and the others keep running: nothing cancels them. The per-id
  `handle` is what turns a bad id into one error row instead of a dead batch.

## Spring integration

[`CatalogAsyncConfig.java`](../../../project2/src/main/java/com/example/project2/config/CatalogAsyncConfig.java)
— one bounded, named, metered pool for the whole app:

```java
@Configuration
@EnableAsync
@EnableConfigurationProperties(CatalogAsyncProperties.class)
@RequiredArgsConstructor
public class CatalogAsyncConfig implements AsyncConfigurer {

    @Bean(name = "catalogAsyncExecutor")
    public ThreadPoolTaskExecutor catalogAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getPoolSize());
        executor.setMaxPoolSize(properties.getPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("catalog-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return catalogAsyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("Async void method {} failed with args {}", method, Arrays.toString(params), throwable);
    }
}
```

- `AsyncConfigurer` makes this the default for every `@Async`, Modulith listeners included.
  Otherwise Spring uses a fresh thread per task.
- `getAsyncUncaughtExceptionHandler` only fires for `@Async void`. A method returning a future
  fails the future instead.

[`ShippingQuoteClient.java`](../../../project2/src/main/java/com/example/project2/client/ShippingQuoteClient.java)
— `@Async` turns a blocking call into a composable stage:

```java
@Async("catalogAsyncExecutor")
public CompletableFuture<ShippingQuoteResponse> quoteAsync(Long productId, BigDecimal weightKg) {
    BigDecimal weight = weightKg == null ? DEFAULT_WEIGHT_KG : weightKg;
    PartnerRate rate = restClient.get()
            .uri("/rates?productId={productId}&weightKg={weightKg}", productId, weight)
            .retrieve()
            .body(PartnerRate.class);
    if (rate == null || rate.amount() == null) {
        throw new IllegalStateException("Shipping partner returned no rate for product " + productId);
    }
    ...
}
```

- `@Async` works through a proxy. Called from inside the same bean it would run on the caller's
  thread; here the caller is the service, a different bean.
- A thrown exception becomes a failed future, which the `exceptionally` above recovers.

[`ContextPropagatingTaskDecorator.java`](../../../project2/src/main/java/com/example/project2/concurrent/ContextPropagatingTaskDecorator.java)
— MDC and the request are ThreadLocals, and they do not follow a stage onto a pool thread:

```java
@Override
public Runnable decorate(Runnable runnable) {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    return () -> {
        applyMdc(contextMap);
        if (requestAttributes != null) {
            RequestContextHolder.setRequestAttributes(requestAttributes);
        }
        try {
            runnable.run();
        } finally {
            MDC.clear();
            RequestContextHolder.resetRequestAttributes();
        }
    };
}
```

- The `finally` clear is the important half. Pool threads are reused, so the next request would
  inherit this trace id.
- Copy `SecurityContextHolder` here too when Security is on the classpath.
  `MODE_INHERITABLETHREADLOCAL` only helps new threads, never pooled ones.

## Endpoints

[`ProductPricingController.java`](../../../project2/src/main/java/com/example/project2/controller/ProductPricingController.java)
— returning the future, not joining it, is what frees the servlet thread:

```java
@GetMapping("/products/{productId}")
public CompletableFuture<ApiResponse<ProductPricingResponse>> pricing(@PathVariable Long productId) {
    return productPricingService.pricing(productId).thenApply(ApiResponse::ok);
}

@GetMapping("/products")
public CompletableFuture<ApiResponse<List<ProductPricingBatchItemResponse>>> pricingBatch(
        @RequestParam("ids") List<Long> ids) {
    return productPricingService.pricingBatch(ids).thenApply(ApiResponse::ok);
}
```

`GET /api/v1/catalog/pricing/products/42` →

```json
{
  "success": true,
  "status": 200,
  "data": {
    "product": { "id": 42, "name": "Aurora Headset", "price": 129.00, "weight": 0.850, "categoryId": 7 },
    "categoryStats": { "categoryId": 7, "productCount": 18432, "averagePrice": 96.44, "priceVsAveragePercent": 33.76 },
    "relatedProducts": [ { "id": 43, "name": "Aurora Headset Stand", "price": 19.00, "stockQuantity": 120 } ],
    "shipping": { "amount": 4.90, "currency": "USD", "source": "FALLBACK" },
    "degraded": true,
    "elapsedMs": 21
  }
}
```

`GET /api/v1/catalog/pricing/products?ids=42,-1` →

```json
{
  "success": true,
  "status": 200,
  "data": [
    { "productId": 42, "pricing": { "degraded": true, "elapsedMs": 18 }, "error": null },
    { "productId": -1, "pricing": null, "error": "Product not found with id: -1" }
  ]
}
```

`GET /api/v1/catalog/pricing/products/-1` → 404 from `GlobalExceptionHandler`: the chain failed
with `CompletionException(BusinessException)` and Spring unwrapped one level.

## Comparison

| Aspect | `Future` + `ExecutorService` ([doc 01](./01-callable-vs-runnable.md)) | `CompletableFuture` chain (this doc) |
|---|---|---|
| What it does | submit, then block on `get()` per task | declare the graph, block nowhere |
| When it applies | a flat fan-out of independent tasks | stages that depend on each other, or need recovery |
| Performance at 1M rows | same query cost; the caller thread is held for the whole fan-out | same query cost; the servlet thread is released at once |
| Under concurrent callers | one caller = one parked request thread + N pool threads | one caller = 0 parked request threads + N pool threads |
| With N instances | pool is per instance either way; nothing is shared | identical |
| Failure mode | `ExecutionException` at `get()`, easy to forget the timeout | a failed stage you must actually consume, or the failure is silent |
| Use when | short fan-out, the caller has nothing else to do | HTTP handlers, dependent stages, timeouts and fallbacks |

Rule of thumb: if you write `get()` immediately after `submit()`, you wanted a chain.

## Under load

| Hazard | What happens with concurrent callers / N instances | What this code does about it |
|---|---|---|
| 24 callers × 5 stages hit a 4-thread pool | an unbounded queue would take all 120 and grow the heap; `AbortPolicy` would throw them away | bounded queue of 128 plus `CallerRunsPolicy` — the submitter runs the stage and the pressure travels back to the client |
| Blocking JDBC on `commonPool` | one thread per core, shared JVM-wide, so parallel streams elsewhere stall too | every `*Async` call is handed `catalogAsyncExecutor` |
| Nested stages waiting on the same pool | classic starvation deadlock: every thread parked on a stage that needs a thread | no stage calls `get()`/`join()` on a stage still running; the test proves it on a 2-thread pool |
| Shipping partner hangs | pool threads pile up on a socket; at N instances that is N × pool-size sockets against one partner | 500ms connect, 800ms read, 1s stage budget, then the flat-rate fallback |
| Pool state is per instance | `catalog-async` queue depth and the FALLBACK counter are per JVM, not global | Micrometer meters are per instance and summed by the scraper; no shared state is kept in the pool |
| Trace id lost in async logs | you cannot follow a request across the stage logs | `ContextPropagatingTaskDecorator` copies MDC in and clears it after |

[`ProductPricingConcurrencyTest.java`](../../../project2/src/test/java/com/example/project2/service/impl/ProductPricingConcurrencyTest.java)
— 24 callers released at once onto a 2-thread pool, which is also the deadlock check:

```java
@SpringBootTest(properties = {
        "catalog.async.pool-size=2",
        "catalog.async.queue-capacity=8",
        "catalog.async.timeout=PT60S",
        "catalog.async.shipping.base-url=http://localhost:9099"
})
class ProductPricingConcurrencyTest {

    @Test
    void concurrentCallers_allGetTheSameFullyMergedAnswer() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(CALLERS);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<ProductPricingResponse>> results = new ArrayList<>();
        for (int i = 0; i < CALLERS; i++) {
            results.add(callers.submit(() -> {
                startGun.await(); // every thread waits here, then they all go at once
                return productPricingService.pricing(productId).get(60, TimeUnit.SECONDS);
            }));
        }
        startGun.countDown();

        for (Future<ProductPricingResponse> result : results) {
            ProductPricingResponse pricing = result.get(60, TimeUnit.SECONDS);
            assertThat(pricing.getCategoryStats().getAveragePrice()).isEqualByComparingTo("103.50");
            assertThat(pricing.getRelatedProducts()).hasSize(RELATED_LIMIT);
            assertThat(pricing.getShipping().getSource()).isEqualTo("FALLBACK");
            assertThat(pricing.getShipping().getAmount()).isEqualByComparingTo("5.00");
        }
    }
}
```

```bash
mvn -pl project2 -am test -Dtest=ProductPricingConcurrencyTest -Dsurefire.failIfNoSpecifiedTests=false
```

The fallback warnings name the thread that ran each one. A caller thread rather than
`catalog-async-N` is `CallerRunsPolicy` handing the stage back — backpressure you can see.

Watch while it runs. Queue depth rising while completions stay flat means the pool, not the
database, is the bottleneck:

```bash
curl localhost:9082/actuator/metrics/executor.queued        # tag pool=catalog-async
curl localhost:9082/actuator/metrics/executor.active
curl localhost:9082/actuator/metrics/hikaricp.connections.pending
curl localhost:9082/actuator/metrics/catalog.shipping.quote # tag source=PARTNER|FALLBACK
```

<!-- not in this repo -->
```bash
# 50 concurrent users for 30s; expect p99 near the slowest stage, not the sum of the five
hey -z 30s -c 50 http://localhost:8088/api/v1/catalog/pricing/products/42
```

H2 answers these queries in microseconds, so the timing margins are far wider there than on
Postgres with ~1M products.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| Building a chain and never consuming it | a failed stage is silent; the exception sits in a future nobody reads |
| `thenApply` where the function returns a future | you get `CompletableFuture<CompletableFuture<T>>` and end up calling `join()` inside the chain |
| `supplyAsync(blockingCall)` with no executor | it runs on `commonPool`, cores − 1 threads, shared with every parallel stream in the JVM |
| `parallelStream()` over blocking calls | same `commonPool`, and it is fully blocked for the duration |
| Blocking inside a stage on the same pool | with a small pool every thread parks waiting for a thread — starvation deadlock |
| Forgetting to unwrap `CompletionException` | `instanceof BusinessException` is false, and the advice answers 500 instead of 404 |
| Expecting `cancel(true)` to interrupt | `CompletableFuture` ignores the flag; the running work continues and only the stage fails |
| `orTimeout` treated as a kill switch | it fails the stage, not the query or the socket behind it |
| `@Async` called from inside the same bean | self-invocation skips the proxy, so it runs on the caller's thread |
| Assuming MDC or `SecurityContext` follows the stage | they are ThreadLocals; without a `TaskDecorator` they are gone |

## Follow-up questions

**`join()` vs `get()`?** Both block. `join()` throws the unchecked `CompletionException`, `get()`
throws the checked `ExecutionException`. Use `join()` inside `thenApply` after `allOf`, where it
cannot actually block.

**How do you wrap a callback-based client?** Return a `new CompletableFuture<>()` and call
`complete(value)` or `completeExceptionally(error)` from the callbacks. That is what manual
completion is for.

**Do virtual threads make this obsolete?** For plain fan-out, largely yes: on Java 21+ you run the
blocking calls on virtual threads and read straight-line code. Structured concurrency
(`StructuredTaskScope`) also gives the fan-out real cancellation, which `allOf` does not.
`CompletableFuture` still wins when you want a callback instead of a waiting thread. Either way JDBC
blocks its carrier thread, so the connection pool stays the limit.

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am spring-boot:run

curl localhost:8088/api/v1/catalog/pricing/products/42        # "source":"FALLBACK", no partner runs locally
curl "localhost:8088/api/v1/catalog/pricing/products?ids=42,43,-1"  # the -1 row carries "error"
curl -i localhost:8088/api/v1/catalog/pricing/products/-1     # 404, not 500 - the unwrapping works
curl localhost:9082/actuator/metrics/executor.queued
```

A second instance behaves identically — the pool and its meters are per JVM and nothing is shared:

```bash
mvn -pl project2 -am spring-boot:run "-Dspring-boot.run.arguments=--server.port=8188 --management.server.port=9182"
```

## References

- [CompletableFuture (Java SE 25 API)](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [JEP 453: Structured Concurrency](https://openjdk.org/jeps/453)
- Related, same topic folder: [Callable vs Runnable](./01-callable-vs-runnable.md)
- Related, other topic folder: [Global exception handling](../rest-api/03-global-exception-handling.md)
