# What is the N+1 query problem and how do you solve it in JPA?

Applied in [project2](../../../project2) (JPA/Hibernate + PostgreSQL, ~1M rows). Every snippet is
copied from the file linked above it, and each of those files back-links to the section here.

| Section | Code in project2 |
|---|---|
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties), [`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties) |
| [Where it happens](#where-it-happens) | [`Order.java`](../../../project2/src/main/java/com/example/project2/entity/Order.java), [`OrderRepository.java`](../../../project2/src/main/java/com/example/project2/repository/OrderRepository.java) |
| [Join fetch](#join-fetch) | [`OrderRepository.java`](../../../project2/src/main/java/com/example/project2/repository/OrderRepository.java) |
| [Entity graph](#entity-graph) | [`OrderRepository.java`](../../../project2/src/main/java/com/example/project2/repository/OrderRepository.java) |
| [DTO projection](#dto-projection) | [`OrderSummaryResponse.java`](../../../project2/src/main/java/com/example/project2/dto/response/OrderSummaryResponse.java) |
| [Batch fetching](#batch-fetching) | [`Order.java`](../../../project2/src/main/java/com/example/project2/entity/Order.java) |
| [Ids then fetch](#ids-then-fetch) | [`OrderRepository.java`](../../../project2/src/main/java/com/example/project2/repository/OrderRepository.java) |
| [Measuring it](#measuring-it) | [`NPlusOneDemoServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/NPlusOneDemoServiceImpl.java), [`NPlusOneDemoController.java`](../../../project2/src/main/java/com/example/project2/controller/NPlusOneDemoController.java), [`OrderRepositoryNPlusOneDataJpaTest.java`](../../../project2/src/test/java/com/example/project2/repository/OrderRepositoryNPlusOneDataJpaTest.java) |
| [Fixing the product listing](#fixing-the-product-listing) | [`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java) |
| [Open session in view](#open-session-in-view) | [`ProductServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductServiceImpl.java) |

## Answer

Loading a list of N parent rows, then reading a lazy field on each one, fires one extra query per
row — that's N+1 queries instead of 1. This isn't a mistake in how the entity is mapped (`LAZY` is
the right default); the real problem is that the query never said what data the caller would
actually need. Fix it at the query itself: `join fetch` / `@EntityGraph` for a single related
object, a DTO projection for read-only listings, `@BatchSize` for collections. Avoid `EAGER` as a
"fix" — it makes every query pay for the join, with no way to opt out. No new dependency needed.

| Fix | Queries | Good for |
|---|---|---|
| `join fetch` (JPQL) | 1 | to-one, one specific query, needs a `countQuery` when paged |
| `@EntityGraph` | 1 | to-one, declarative, reusable, works on derived queries |
| DTO projection | 1 | read-only listings; no entities, no proxies, fewest columns |
| `@BatchSize` / `default_batch_fetch_size` | 1 + ceil(N/size) | collections, and as a global safety net |
| Ids, then fetch | 2 | a collection *and* pagination in the same query |
| `EAGER` | 1, always | nothing — it is not opt-out-able per query |

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.default_batch_fetch_size=50
spring.jpa.properties.hibernate.generate_statistics=true
spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true
```

| Choice | vs the alternative |
|---|---|
| `open-in-view=false` | vs Boot's default (`true`): a missed fetch now fails loudly with `LazyInitializationException` in tests, instead of quietly firing extra queries during JSON serialization |
| `default_batch_fetch_size=50` | vs Hibernate's default (off): if a fetch is missed, this caps the damage at about N/50 extra queries instead of N — it never makes a query worse |
| `generate_statistics=true` | vs `false`: turns on the statement counters this demo relies on, for a small performance cost |
| `fail_on_pagination_over_collection_fetch=true` | vs Hibernate's default (`false`): pairing `join fetch` with pagination now throws an exception right away, instead of silently loading the whole table into memory |

[`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties)
— the same knobs set the noisy/unsafe way on purpose:

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
spring.jpa.open-in-view=true
spring.jpa.properties.hibernate.default_batch_fetch_size=-1
```

## Where it happens

[`Order.java`](../../../project2/src/main/java/com/example/project2/entity/Order.java):

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

[`OrderRepository.java`](../../../project2/src/main/java/com/example/project2/repository/OrderRepository.java)
— kept un-fetched deliberately, as the "before" side of the comparison:

```java
Page<Order> findByStatus(String status, Pageable pageable);
```

[`NPlusOneDemoServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/NPlusOneDemoServiceImpl.java):

```java
Page<Order> page = orderRepository.findByStatus(status, pageable);
// getUser() is a proxy here: one extra select per row, fired inside the mapping loop.
List<OrderSummaryResponse> orders = page.getContent().stream().map(this::toSummary).toList();
```

- Nothing is wrong yet when the repository call returns — the extra queries only fire once the
  code reads `order.getUser().getEmail()` for each row, further down.
- If every order shared the same buyer, Hibernate's cache would hide the problem — that's why the
  test data uses a different buyer per order.

## Join fetch

[`OrderRepository.java`](../../../project2/src/main/java/com/example/project2/repository/OrderRepository.java):

```java
@Query(value = """
        select o from Order o
        join fetch o.user
        where o.status = :status
        """,
        countQuery = "select count(o) from Order o where o.status = :status")
Page<Order> findByStatusJoinFetchUser(@Param("status") String status, Pageable pageable);
```

- You have to write your own `countQuery` — Hibernate can't work one out on its own when a fetch
  join is involved.
- A plain `join` only filters rows; adding `fetch` is what actually loads the related data.

## Entity graph

[`OrderRepository.java`](../../../project2/src/main/java/com/example/project2/repository/OrderRepository.java):

```java
@EntityGraph(attributePaths = "user")
Page<Order> findWithUserByStatus(String status, Pageable pageable);
```

- The words between `find` and `By` don't change the query at all — `WithUser` is just there to
  describe what the `@EntityGraph` does.
- This generates a `LEFT JOIN FETCH`, so Spring Data can still work out the count query by itself,
  and a row with a null foreign key isn't dropped.

## DTO projection

[`OrderSummaryResponse.java`](../../../project2/src/main/java/com/example/project2/dto/response/OrderSummaryResponse.java)
— the constructor the query binds to:

```java
public OrderSummaryResponse(Long id, String orderNumber, String status, BigDecimal totalAmount,
                            Long userId, String userEmail) {
    this(id, orderNumber, status, totalAmount, userId, userEmail, null);
}
```

[`OrderRepository.java`](../../../project2/src/main/java/com/example/project2/repository/OrderRepository.java):

```java
@Query(value = """
        select new com.example.project2.dto.response.OrderSummaryResponse(
            o.id, o.orderNumber, o.status, o.totalAmount, u.id, u.email)
        from Order o
        join o.user u
        where o.status = :status
        """,
        countQuery = "select count(o) from Order o where o.status = :status")
Page<OrderSummaryResponse> findSummaryByStatus(@Param("status") String status, Pageable pageable);
```

- The result isn't a managed entity, so there's nothing to trigger a later lazy load and no
  change-tracking overhead per row.
- If the constructor's argument order or types don't match the query, you won't find out until
  runtime (a `SemanticException`), never at compile time.

## Batch fetching

[`Order.java`](../../../project2/src/main/java/com/example/project2/entity/Order.java):

```java
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
@BatchSize(size = 50)
@Builder.Default
private List<OrderDetail> details = new ArrayList<>();
```

- For 20 orders, this fires one select with `where order_id in (?,?,…)` instead of 20 separate ones.
- `@BatchSize` on a field overrides the global `default_batch_fetch_size`, even to a smaller value.

## Ids then fetch

[`OrderRepository.java`](../../../project2/src/main/java/com/example/project2/repository/OrderRepository.java):

```java
@Query(value = "select o.id from Order o where o.status = :status",
        countQuery = "select count(o) from Order o where o.status = :status")
Page<Long> findIdsByStatus(@Param("status") String status, Pageable pageable);

@EntityGraph(attributePaths = {"user", "details"})
List<Order> findWithUserAndDetailsByIdIn(List<Long> ids);
```

[`NPlusOneDemoServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/NPlusOneDemoServiceImpl.java):

```java
Page<Long> idPage = orderRepository.findIdsByStatus(status, pageable);
Map<Long, Order> byId = orderRepository.findWithUserAndDetailsByIdIn(idPage.getContent()).stream()
        .collect(Collectors.toMap(Order::getId, Function.identity()));
// The IN query has no ORDER BY of its own - replay the id page's order.
List<OrderSummaryResponse> orders = idPage.getContent().stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .map(this::toSummaryWithItems)
        .toList();
```

- This is the only way to page through a collection while still letting the database apply
  `LIMIT`: the first query has no join, and the second has no `LIMIT`.
- The second query doesn't preserve order, so the code re-sorts the results to match the original
  page — otherwise rows come back scrambled.

## Measuring it

[`NPlusOneDemoServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/NPlusOneDemoServiceImpl.java):

```java
Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

// Cold persistence context, otherwise a previous strategy's cached User proxies hide the N+1.
entityManager.clear();
long statementsBefore = statistics.getPrepareStatementCount();
```

`GET /api/v1/performance/n-plus-one/compare?status=PENDING&size=20` →

```json
{
  "success": true,
  "data": [
    { "strategy": "NAIVE",          "rows": 5, "sqlStatements": 6, "collectionsFetched": 0, "note": "1 query for the page + 1 per row for Order.user" },
    { "strategy": "JOIN_FETCH",     "rows": 5, "sqlStatements": 1, "collectionsFetched": 0 },
    { "strategy": "ENTITY_GRAPH",   "rows": 5, "sqlStatements": 1, "collectionsFetched": 0 },
    { "strategy": "DTO_PROJECTION", "rows": 5, "sqlStatements": 1, "collectionsFetched": 0 },
    { "strategy": "BATCH_FETCH",    "rows": 5, "sqlStatements": 2, "collectionsFetched": 1 },
    { "strategy": "IDS_THEN_FETCH", "rows": 5, "sqlStatements": 2, "collectionsFetched": 0 }
  ]
}
```

Those counts are asserted against a 5-order fixture — only `NAIVE` scales with the page (21
statements for 20 rows) — in
[`OrderRepositoryNPlusOneDataJpaTest.java`](../../../project2/src/test/java/com/example/project2/repository/OrderRepositoryNPlusOneDataJpaTest.java):

```java
@Test
void findByStatus_withoutFetch_firesOneSelectPerRow() {
    long statements = countStatements(() -> orderRepository.findByStatus("PENDING", FIRST_PAGE)
            .getContent()
            .forEach(order -> order.getUser().getEmail()));

    assertThat(statements).isEqualTo(1 + ORDERS); // the N+1, measured
}
```

- The test asserts on the number of SQL statements, not on timing — that's reliable, and it fails
  the build if someone later removes a fetch join by accident.
- `getCollectionFetchCount()` counts round trips, not collections: 5 collections loaded in one
  batched query count as 1, and a collection loaded via join fetch counts as 0.

## Fixing the product listing

`GET /api/v1/products` had the same bug — `ProductServiceImpl.toResponse` reads
`product.getCategory().getName()` per row.

[`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java):

```java
@Override
@EntityGraph(attributePaths = "category")
Page<Product> findAll(Pageable pageable);
```

- Overriding the inherited `findAll(Pageable)` fixes the problem for every caller, without
  changing any of them.
- `category` is a single related object, not a list, so the join can't duplicate rows and paging
  still works normally.

## Open session in view

[`ProductServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductServiceImpl.java):

```java
@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::toResponse);
    }
```

- With `open-in-view=false`, these read methods need their own transaction, or reading the lazy
  `category` field throws `LazyInitializationException`.
- That's actually the point: with OSIV on, the extra queries would fire silently during JSON
  serialization, somewhere tests don't usually check.

## Comparison

| Aspect | `join fetch` / `@EntityGraph` | `@BatchSize` | DTO projection |
|---|---|---|---|
| What it does | one query, association joined in | groups the extra loads into `in (...)` batches | selects columns, no entities |
| When it applies | per query (graph) or per query string (JPQL) | per association or globally, whenever a proxy is touched | per query |
| Performance | 1 round trip; wide rows, duplicates on collections | 1 + ceil(N/size) round trips | 1 round trip, narrowest rows |
| Failure mode | pairing with pagination on a collection loads everything into memory; fetching two collections at once throws `MultipleBagFetchException` | still runs several queries (just fewer of them); not much help for deeply nested data | returns plain data, not entities — can't be saved back |
| Use when | you know the caller reads that association | you cannot change the query, or as a safety net | read-only listing/report |

Rule of thumb: to-one → `@EntityGraph`; paged collection → ids then fetch; listing screen →
projection; anything missed → `default_batch_fetch_size`.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| `FetchType.EAGER` as the fix | every query pays for the join whether it needs it or not, and paging an eager collection is a classic cause of out-of-memory errors |
| `join fetch` + `Pageable` on a collection | Hibernate can't apply `LIMIT` in the database, so it loads the whole result into memory instead (`fail_on_pagination_over_collection_fetch=true` turns this into an exception instead) |
| Two `List` fetch joins in one query | throws `MultipleBagFetchException` at startup — use `Set` instead, or load the second collection separately with `@BatchSize` |
| `join fetch` with a derived count query | Hibernate can't build the count query on its own here — supply `countQuery` yourself |
| Benchmarking with rows that share an FK | Hibernate's cache hides the repeats, making the N+1 look like just 1 extra query |
| Relying on the count query being cheap | every strategy pays for one extra `count(*)` per full page; only a partial (last) page skips it |
| `@Transactional(readOnly = true)` on the service as an "N+1 fix" | doesn't reduce the number of queries at all — it just lets lazy loading succeed instead of throwing |

## Follow-up questions

**Is `@EntityGraph` the same as `join fetch`?** They produce similar SQL (though `@EntityGraph`
defaults to `LEFT JOIN`, `join fetch` to `INNER`). The real difference is how you declare them:
the graph works on derived query methods and lets Spring Data figure out the count query for you;
JPQL is a query you write by hand.

**`@EntityGraph` FETCH vs LOAD?** `FETCH` (the default) makes the listed fields eager and
everything else lazy. `LOAD` makes the listed fields eager but leaves everything else as
originally mapped.

**Does the second-level cache solve N+1?** Only on a cache hit — the code still asks once per
parent either way. It softens the problem, it doesn't fix it.

**Why not always use a projection?** Because the result isn't a managed entity — no change
tracking, no cascading, no lazy navigation. It's read-only.

**How do you detect it in production?** Turn on `generate_statistics=true` and watch the
`hibernate.*` metrics on `/actuator/prometheus` ([01-spring-boot-actuator.md](./01-spring-boot-actuator.md)),
or catch it earlier with statement-count assertions like the ones above, in CI.

## Try it

```bash
cd back-end/spring-boot/practice
docker-compose up -d postgres
mvn -pl project2 -am spring-boot:run -Dspring-boot.run.profiles=dev   # dev: SQL logged to stdout

# 6 strategies, same page, statement count each - NAIVE is the only one that scales with size
curl "http://localhost:8088/api/v1/performance/n-plus-one/compare?status=PENDING&size=20"

# one strategy at a time; watch the log with the dev profile active
curl "http://localhost:8088/api/v1/performance/n-plus-one?strategy=NAIVE&size=20"
curl "http://localhost:8088/api/v1/performance/n-plus-one?strategy=ENTITY_GRAPH&size=20"

# the fixed listing: 1 select for the page + its count, no per-row category lookup
curl "http://localhost:8088/api/v1/products?size=20"
```

No database needed for the measurement:

```bash
mvn -pl project2 -am test -Dtest=OrderRepositoryNPlusOneDataJpaTest    # H2, 6 query-count assertions
```

## References

- [Spring Data JPA — Entity graphs](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-graph.html)
- [Hibernate ORM 7 — Fetching](https://docs.jboss.org/hibernate/orm/7.0/userguide/html_single/Hibernate_User_Guide.html#fetching)
- [Hibernate `QuerySettings`](https://docs.hibernate.org/orm/7.0/javadocs/org/hibernate/cfg/QuerySettings.html)
  — `fail_on_pagination_over_collection_fetch`, default `false`
- Related, same folder: [01-spring-boot-actuator.md](./01-spring-boot-actuator.md) (`hibernate.*`
  meters), [02-test-slices-vs-mockito.md](./02-test-slices-vs-mockito.md) (`@DataJpaTest`)
