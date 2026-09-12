# Explain idempotency in REST APIs. Which HTTP methods are idempotent and why does it matter?

Applied in [project2](../../../project2) (Web + JPA + PostgreSQL; the key store needs a real unique
index and `ON CONFLICT`). Each block links its source file, and each of those files back-links here.

| Section | Code in project2 |
|---|---|
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties), [`IdempotencyProperties.java`](../../../project2/src/main/java/com/example/project2/config/IdempotencyProperties.java), [`IdempotencyConfig.java`](../../../project2/src/main/java/com/example/project2/config/IdempotencyConfig.java) |
| [The store](#the-store) | [`V10__create_idempotency_records.sql`](../../../project2/src/main/resources/db/changelog/changes/V10__create_idempotency_records.sql), [`IdempotencyRecord.java`](../../../project2/src/main/java/com/example/project2/entity/IdempotencyRecord.java), [`IdempotencyStatus.java`](../../../project2/src/main/java/com/example/project2/entity/IdempotencyStatus.java), [`IdempotencyRecordRepository.java`](../../../project2/src/main/java/com/example/project2/repository/IdempotencyRecordRepository.java) |
| [Claim, run, record](#claim-run-record) | [`IdempotencyService.java`](../../../project2/src/main/java/com/example/project2/service/IdempotencyService.java), [`IdempotencyServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/IdempotencyServiceImpl.java), [`IdempotencyRecordStore.java`](../../../project2/src/main/java/com/example/project2/service/impl/IdempotencyRecordStore.java) |
| [The POST endpoint](#the-post-endpoint) | [`IdempotentCheckoutController.java`](../../../project2/src/main/java/com/example/project2/controller/IdempotentCheckoutController.java) |
| [PUT and DELETE](#put-and-delete) | [`OrderServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/OrderServiceImpl.java), [`ReplaceShippingAddressRequest.java`](../../../project2/src/main/java/com/example/project2/dto/request/ReplaceShippingAddressRequest.java) |
| [Cleanup](#cleanup) | [`IdempotencyCleanupJob.java`](../../../project2/src/main/java/com/example/project2/service/impl/IdempotencyCleanupJob.java) |

## Answer

A method is idempotent when sending the same request twice leaves the server in the same state as
sending it once. It says nothing about the response: a second `DELETE` may return 404 and still be
idempotent. Safe is stronger and different — a safe method changes no state at all.

`GET`, `HEAD`, `OPTIONS` and `TRACE` are safe, and therefore idempotent. `PUT` and `DELETE` change
state but are idempotent because they set an absolute result. `POST` and `PATCH` are neither.

It matters because a timeout means the response was lost, not that the request failed. Clients,
proxies, gateways and queues all retry, and only an idempotent call is safe to retry blindly. For
`POST` you buy that property with an `Idempotency-Key` header.

| Method | Safe | Idempotent | Why |
|---|---|---|---|
| `GET`, `HEAD` | yes | yes | reads only |
| `OPTIONS`, `TRACE` | yes | yes | describes the resource, changes nothing |
| `PUT` | no | yes | writes an absolute state, so the second write is a no-op |
| `DELETE` | no | yes | after the first call the resource is gone and stays gone |
| `POST` | no | **no** | each call creates another resource |
| `PATCH` | no | **not by default** | `{"stock": 10}` is idempotent, `{"op": "increment"}` is not |

No new dependency needed.

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
idempotency.ttl=PT24H
idempotency.cleanup-interval=PT15M
idempotency.cleanup-batch-size=500
```

| Choice | vs the alternative |
|---|---|
| `ttl=PT24H` | vs `PT5M`: five minutes covers the automatic retry but not the customer who reloads the page an hour later. Then you have two orders. |
| `cleanup-batch-size=500` | vs one unbounded `DELETE`: a single statement over millions of expired rows takes locks that live checkouts queue behind. |
| `cleanup-interval=PT15M` | vs cleaning up inside the request: that would make some checkouts randomly slow. |
| Keys in Postgres | vs Redis: same database as the order, so the claim and the order can be reasoned about together. Redis is faster but adds a second store that can disagree. |

[`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties)
— the same knobs set the wrong way on purpose:

```properties
idempotency.ttl=PT2M
idempotency.cleanup-interval=PT30S
```

[`IdempotencyProperties.java`](../../../project2/src/main/java/com/example/project2/config/IdempotencyProperties.java):

```java
@Getter
@Setter
@ConfigurationProperties(prefix = "idempotency")
public class IdempotencyProperties {

    private Duration ttl = Duration.ofHours(24);
    private Duration cleanupInterval = Duration.ofMinutes(15);
    private int cleanupBatchSize = 500;
}
```

## The store

[`V10__create_idempotency_records.sql`](../../../project2/src/main/resources/db/changelog/changes/V10__create_idempotency_records.sql):

```sql
CREATE TABLE idempotency_records (
    idempotency_key     VARCHAR(120)             NOT NULL,
    request_target      VARCHAR(200)             NOT NULL,
    request_fingerprint CHAR(64)                 NOT NULL,
    status              VARCHAR(20)              NOT NULL,
    response_status     INTEGER,
    response_body       TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at        TIMESTAMP WITH TIME ZONE,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_idempotency_records PRIMARY KEY (idempotency_key)
);

CREATE INDEX idx_idempotency_records_expires_at ON idempotency_records (expires_at);
```

- The primary key is the lock: two simultaneous retries both try to insert it and the database picks
  one winner.
- `request_fingerprint` is a SHA-256 of the body, so a key reused for a different order gets an
  error instead of someone else's response.

[`IdempotencyRecordRepository.java`](../../../project2/src/main/java/com/example/project2/repository/IdempotencyRecordRepository.java)
— claiming a key is one statement:

```java
@Modifying
@Query(value = """
        INSERT INTO idempotency_records
            (idempotency_key, request_target, request_fingerprint, status, created_at, expires_at)
        VALUES (:key, :target, :fingerprint, 'IN_PROGRESS', :now, :expiresAt)
        ON CONFLICT (idempotency_key) DO NOTHING
        """, nativeQuery = true)
int insertClaim(@Param("key") String key,
                @Param("target") String target,
                @Param("fingerprint") String fingerprint,
                @Param("now") Instant now,
                @Param("expiresAt") Instant expiresAt);
```

- Returns 1 when this call won the key and 0 when someone else already holds it.
- A `SELECT` then `INSERT` in Java would let both retries pass the select and place two orders.

## Claim, run, record

[`IdempotencyRecordStore.java`](../../../project2/src/main/java/com/example/project2/service/impl/IdempotencyRecordStore.java):

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean claim(String key, String target, String fingerprint, Instant now, Duration ttl) {
    return repository.insertClaim(key, target, fingerprint, now, now.plus(ttl)) == 1;
}
```

- `REQUIRES_NEW` commits the claim straight away, so a parallel retry sees it while the order is
  still being created.
- It needs its own bean: a `REQUIRES_NEW` call on `this` skips the proxy and stays in the caller's
  transaction.

[`IdempotencyServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/IdempotencyServiceImpl.java):

```java
@Override
public <T> IdempotentResult<T> execute(IdempotentRequest request, Class<T> responseType, Supplier<T> action) {
    String fingerprint = fingerprint(request.payload());

    for (int attempt = 1; attempt <= MAX_CLAIM_ATTEMPTS; attempt++) {
        Instant now = Instant.now();
        if (store.claim(request.key(), request.requestTarget(), fingerprint, now, properties.getTtl())) {
            return runAndRecord(request, responseType, action);
        }

        Optional<IdempotencyRecord> existing = store.find(request.key());
        if (existing.isEmpty()) {
            continue; // the holder released the key between our insert and our read
        }
        if (existing.get().isExpired(now)) {
            store.release(request.key());
            continue;
        }
        return replay(existing.get(), request, fingerprint, responseType);
    }

    throw new BusinessException(
            "Idempotency-Key " + request.key() + " is contended, retry in a moment",
            HttpStatus.CONFLICT, "IDEMPOTENCY_CONTENDED");
}
```

- The class is deliberately not `@Transactional`. A claim inside the order's transaction would only
  appear at commit, which is too late.

[`IdempotencyServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/IdempotencyServiceImpl.java)
— what happens around the business call:

```java
private <T> IdempotentResult<T> runAndRecord(IdempotentRequest request, Class<T> responseType, Supplier<T> action) {
    try {
        T body = action.get();
        store.complete(request.key(), request.successStatus(), toJson(body));
        log.info("Idempotency key {} completed with status {}", request.key(), request.successStatus());
        return new IdempotentResult<>(body, request.successStatus(), false);
    } catch (RuntimeException ex) {
        // Nothing was created, so the client must be able to retry with the same key.
        store.release(request.key());
        throw ex;
    }
}
```

- Only successes are stored. Keeping a failure would answer every later retry with the same 500.

| A second request finds | Response |
|---|---|
| no row | runs the action, 201 |
| `COMPLETED`, same fingerprint | 201 with the stored body and `Idempotency-Replayed: true` |
| `IN_PROGRESS`, same fingerprint | 409 `IDEMPOTENCY_IN_PROGRESS`, retry in a moment |
| any status, different fingerprint | 422 `IDEMPOTENCY_KEY_REUSED` |
| an expired row | row deleted, request treated as new |

## The POST endpoint

[`IdempotentCheckoutController.java`](../../../project2/src/main/java/com/example/project2/controller/IdempotentCheckoutController.java):

```java
@PostMapping("/orders")
public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
        @RequestHeader("Idempotency-Key")
        @NotBlank(message = "Idempotency-Key must not be blank")
        @Size(max = 120, message = "Idempotency-Key must be at most 120 characters")
        String idempotencyKey,
        @Valid @RequestBody CreateOrderRequest request) {

    IdempotentResult<OrderResponse> result = idempotencyService.execute(
            new IdempotentRequest(idempotencyKey, "POST " + ORDERS_PATH, request,
                    HttpStatus.CREATED.value()),
            OrderResponse.class,
            () -> orderService.create(request));

    return ResponseEntity.status(result.status())
            .header(REPLAYED_HEADER, Boolean.toString(result.replayed()))
            .body(ApiResponse.created(result.body()));
}
```

- The header is required: a missing key is a 400, which beats a silent chance of a duplicate order.
- The client generates the key, usually a UUID, and reuses it for every retry of that checkout.

`POST /api/v1/checkout/orders` with `Idempotency-Key: 9f1c0f6e-...` →

```json
{
  "success": true,
  "status": 201,
  "message": "Created successfully",
  "data": {
    "id": 4211,
    "orderNumber": "ORD-1757671234567-3f9ac21b",
    "status": "PENDING",
    "totalAmount": 259.98
  }
}
```

The same call again returns the same `id` and `orderNumber`, with `Idempotency-Replayed: true`.

## PUT and DELETE

[`OrderServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/OrderServiceImpl.java)
— behind `PUT /api/v1/checkout/orders/{id}/addresses`, both fields are written every time:

```java
@Override
public OrderResponse replaceAddresses(Long id, ReplaceShippingAddressRequest request) {
    log.debug("Replacing addresses on order id: {}", id);
    Order order = orderRepository.findById(id)
            .orElseThrow(() -> BusinessException.notFound("Order not found with id: " + id));

    order.setShippingAddress(request.getShippingAddress());
    order.setBillingAddress(request.getBillingAddress());

    Order saved = orderRepository.save(order);
    log.info("Replaced addresses on order id: {}", saved.getId());
    return toResponse(saved);
}
```

- Compare `update(Long, UpdateOrderRequest)` just above it: that one backs a `PATCH` and skips null
  fields, so the result depends on what was there before.
- [`ReplaceShippingAddressRequest.java`](../../../project2/src/main/java/com/example/project2/dto/request/ReplaceShippingAddressRequest.java)
  makes both fields `@NotBlank`, so a `PUT` cannot half-describe the new state.

[`OrderServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/OrderServiceImpl.java)
— behind `DELETE /api/v1/checkout/orders/{id}`, which answers 204 on every call:

```java
@Override
public boolean deleteIfExists(Long id) {
    log.debug("Deleting order if present, id: {}", id);
    if (!orderRepository.existsById(id)) {
        log.debug("Order {} already deleted, nothing to do", id);
        return false;
    }
    orderRepository.deleteById(id);
    log.info("Deleted order with id: {}", id);
    return true;
}
```

- 204 on a repeat is kinder to a retrying client than 404, and both are idempotent.
- Return 404 instead when the caller needs to know whether it was the one that deleted the row.

## Cleanup

[`IdempotencyCleanupJob.java`](../../../project2/src/main/java/com/example/project2/service/impl/IdempotencyCleanupJob.java):

```java
@Scheduled(fixedDelayString = "${idempotency.cleanup-interval:PT15M}")
public void purgeExpiredKeys() {
    int deleted = store.purgeExpired(Instant.now(), properties.getCleanupBatchSize());
    if (deleted > 0) {
        log.info("Purged {} expired idempotency records", deleted);
    }
}
```

- `fixedDelay` waits for the previous sweep to finish, so a slow delete cannot pile runs on top of
  each other.
- Every instance sweeps. Harmless here, but a job that must run once needs ShedLock or a leader
  election.

## Comparison

| Aspect | Idempotent by method (`PUT`, `DELETE`) | `POST` + `Idempotency-Key` |
|---|---|---|
| What it does | the request states the final result | the server remembers the first result |
| When it applies | the client knows the resource id | the server assigns the id |
| Performance | nothing extra | one insert and one update per call, both by primary key |
| Failure mode | a stale `PUT` overwrites a newer write, so add `If-Match` | key expires too early, or sticks in `IN_PROGRESS` after a crash |
| Use when | updating or deleting a known resource | create, pay, ship, send |

Rule of thumb: use `PUT` when the client can name the resource, and `POST` with a key when it
cannot.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| `SELECT` then `INSERT` instead of a unique key | two parallel retries both pass the select and both create an order |
| Writing the key in the same transaction as the work | the row is invisible until commit, which is the window you were trying to protect |
| Storing failed attempts | every retry replays the same 500 and the client can never recover |
| No fingerprint check | a client that reuses a key for a different order silently gets the old order back |
| TTL shorter than the client's retry window | the key expires, the retry looks new, the customer is charged twice |
| Crash between the commit and `complete()` | the row stays `IN_PROGRESS` and retries get 409 until the TTL runs out |
| Calling `PATCH` idempotent | it only is when the body is absolute; an `increment` body is not |
| Relying on 404 from a repeated `DELETE` | a proxy may have retried it already, so an honest client sees 404 for a delete that worked |

## Follow-up questions

**Is idempotent the same as safe?** No. Safe means no state change at all (`GET`). Every safe method
is idempotent, not the other way round.

**Must a retried request return the same status code?** No, idempotency is about state. Replaying
the stored 201 is just a nicer API, which is why `execute` stores the status.

**Where does the key come from?** The client, one UUID per business operation. A server-generated
key would differ on the retry, so it would protect nothing.

**Can `POST` ever be idempotent?** Only if you make it so. Besides a key, use a natural unique
constraint such as one order per cart id, or `PUT /orders/{clientGeneratedUuid}`.

**What about a gateway that retries?** It resends the original headers, so the key comes with it and
the second attempt replays.

**How does this relate to at-least-once messaging?** Same problem, same fix: use the message id as
the key and a duplicate delivery cannot charge twice.

**Why 409 while the first request is still running?** The client should retry rather than get a
half-finished answer. `Retry-After` tells it when.

## Try it

```bash
cd back-end/spring-boot/practice
docker-compose up -d postgresql          # local Postgres on localhost:5434
mvn -pl project2 -am spring-boot:run

KEY=$(uuidgen)

# Place an order, then run the exact same command again.
curl -i -X POST localhost:8088/api/v1/checkout/orders \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d '{"userId":1,"items":[{"productId":1,"quantity":2}]}'
# first  -> 201, Idempotency-Replayed: false
# second -> 201, Idempotency-Replayed: true, same order id

# Same key, different body -> 422 IDEMPOTENCY_KEY_REUSED
curl -i -X POST localhost:8088/api/v1/checkout/orders \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d '{"userId":1,"items":[{"productId":1,"quantity":9}]}'

# No key at all -> 400
curl -i -X POST localhost:8088/api/v1/checkout/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"items":[{"productId":1,"quantity":2}]}'

# PUT twice -> 200 both times, same final state
curl -i -X PUT localhost:8088/api/v1/checkout/orders/1/addresses \
  -H "Content-Type: application/json" \
  -d '{"shippingAddress":"1 Main St","billingAddress":"1 Main St"}'

# DELETE twice -> 204 both times
curl -i -X DELETE localhost:8088/api/v1/checkout/orders/1
```

## References

- [RFC 9110 section 9.2.2 - Idempotent Methods](https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2)
- [The Idempotency-Key HTTP Header Field (IETF draft)](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/)
- [Spring transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
- Related, same topic folder: [03-global-exception-handling.md](./03-global-exception-handling.md), [01-rest-api-versioning.md](./01-rest-api-versioning.md)
- Related, other topic folder: [database/01-database-migrations.md](../database/01-database-migrations.md)
