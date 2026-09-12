# How do you implement pagination and sorting in Spring Data REST?

Applied in [project2](../../../project2) (Web + JPA + PostgreSQL, ~1M product rows). Each block
links its source file, and those files link back here.

| Section | Code in project2 |
|---|---|
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties), [`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties) |
| [Query parameters](#query-parameters) | [`ProductCatalogController.java`](../../../project2/src/main/java/com/example/project2/controller/ProductCatalogController.java) |
| [Paged endpoint](#paged-endpoint) | [`ProductCatalogController.java`](../../../project2/src/main/java/com/example/project2/controller/ProductCatalogController.java), [`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java), [`ProductSummaryResponse.java`](../../../project2/src/main/java/com/example/project2/dto/response/ProductSummaryResponse.java) |
| [Defaults](#defaults) | [`ProductCatalogController.java`](../../../project2/src/main/java/com/example/project2/controller/ProductCatalogController.java) |
| [Guarding the sort](#guarding-the-sort) | [`ProductCatalogServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductCatalogServiceImpl.java), [`V11__add_product_sort_indexes.sql`](../../../project2/src/main/resources/db/changelog/changes/V11__add_product_sort_indexes.sql) |
| [Slice and cursor-based pagination](#slice-and-cursor-based-pagination) | [`ProductCatalogService.java`](../../../project2/src/main/java/com/example/project2/service/ProductCatalogService.java), [`ProductScrollResponse.java`](../../../project2/src/main/java/com/example/project2/dto/response/ProductScrollResponse.java) |

## Answer

Put a `Pageable` in the handler method and return a `Page<T>`. Spring's
`PageableHandlerMethodArgumentResolver` reads `page`, `size` and `sort` off the query string, and
Spring Data turns them into `LIMIT`, `OFFSET` and `ORDER BY`.

`Page` costs a second query to count every matching row. `Slice` drops that count, and a plain
`List` gives you the rows only.

Spring gives you neither a sort allow-list nor a depth cap. Both are yours to write.

No new dependency — Spring Data web support is on by default in Boot.

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
spring.data.web.pageable.default-page-size=20
spring.data.web.pageable.max-page-size=100
spring.data.web.pageable.one-indexed-parameters=false
spring.data.web.pageable.page-parameter=page
spring.data.web.pageable.size-parameter=size
spring.data.web.sort.sort-parameter=sort
```

| Choice | vs the alternative |
|---|---|
| `max-page-size=100` | vs Spring's default `2000`: one careless `?size=2000` reads 2000 of a million rows and serializes them all. |
| `default-page-size=20` | vs `size` missing entirely: without a default, a forgetful client would get the resolver's built-in 20 anyway — setting it makes the contract explicit. |
| `one-indexed-parameters=false` | vs `true`: 1-based URLs read nicer, but `Page.getNumber()` still returns 0-based, so every log line is off by one. |
| `page-parameter` / `size-parameter` | Rename only to match a legacy client's URLs. Changing them is a breaking API change. |
| Explicit `countQuery` | vs letting Spring derive one: it cannot derive a count from a `select new ...` projection. |
| `PagedModel` per endpoint | vs `spring.data.web.pageable.serialization-mode=VIA_DTO`: the property changes every endpoint's JSON at once, which breaks clients you did not mean to touch. |

[`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties)
— the same knob loosened on purpose:

```properties
spring.data.web.pageable.max-page-size=2000
#spring.data.web.pageable.one-indexed-parameters=true
```

## Query parameters

| URL | Meaning |
|---|---|
| `?page=0&size=10` | first page, 10 rows |
| `?sort=name,asc` | property first, direction second; direction defaults to `asc` |
| `?sort=name,asc&sort=id,desc` | multi-sort — repeat the parameter, order matters |
| `?sort=name&sort=id,desc` | same thing; `name` falls back to `asc` |
| `?sort=price,desc&sort=id,asc` | the tiebreak that keeps rows off two pages at once |
| `?size=5000` | clamped to `max-page-size`, not rejected |
| `?sort=description` | 400 here, because of the allow-list below |

## Paged endpoint

[`ProductCatalogController.java`](../../../project2/src/main/java/com/example/project2/controller/ProductCatalogController.java):

```java
@GetMapping
public ApiResponse<PagedModel<ProductSummaryResponse>> findPage(
        @PageableDefault(size = 20)
        @SortDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
        Pageable pageable) {
    return ApiResponse.ok(new PagedModel<>(productCatalogService.findPage(pageable)));
}
```

- `PagedModel` (from `org.springframework.data.web`, not HATEOAS) keeps the JSON shape stable.
  Returning the `Page` itself publishes Spring Data internals as your contract.

[`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java):

```java
@Query(value = """
        select new com.example.project2.dto.response.ProductSummaryResponse(
            p.id, p.name, p.brand, p.price, p.stockQuantity)
        from Product p
        """,
        countQuery = "select count(p) from Product p")
Page<ProductSummaryResponse> findSummaries(Pageable pageable);
```

- Spring Data appends the caller's sort to the query as `order by p.<property>`.
- The projection skips the TEXT `description`, which nobody reads in a list.

`GET /api/v1/catalog/products?page=0&size=2&sort=price,desc&sort=id,asc` →

```json
{
  "success": true,
  "status": 200,
  "message": "Success",
  "data": {
    "content": [
      { "id": 812, "name": "Studio Monitor 8\"", "brand": "Acme", "price": 4999.00, "stockQuantity": 7 },
      { "id": 913, "name": "Reference Amp", "brand": "Acme", "price": 4999.00, "stockQuantity": 2 }
    ],
    "page": { "size": 2, "number": 0, "totalElements": 1000000, "totalPages": 500000 }
  }
}
```

## Defaults

| Where | What it sets | Wins over |
|---|---|---|
| `spring.data.web.pageable.*` | every endpoint in the app | the resolver's built-in defaults |
| `@PageableDefault(size = 20)` | this parameter's page and size | the global properties |
| `@SortDefault(sort = {...}, direction = DESC)` | this parameter's sort only | `@PageableDefault`'s own `sort` |
| the request's own `?page=&size=&sort=` | wins over all of the above | — |

- One `@SortDefault` applies its direction to all its properties. Use `@SortDefault.SortDefaults`
  when two properties need different directions.

## Guarding the sort

[`ProductCatalogServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductCatalogServiceImpl.java):

```java
private static final List<String> SORTABLE = List.of("createdAt", "id", "name", "price");
private static final long MAX_OFFSET = 10_000L;

private void rejectUnknownSort(Sort sort) {
    List<String> unknown = sort.stream()
            .map(Sort.Order::getProperty)
            .filter(property -> !SORTABLE.contains(property))
            .toList();
    if (!unknown.isEmpty()) {
        throw BusinessException.badRequest(
                "Cannot sort products by " + unknown + ". Sortable properties: " + SORTABLE);
    }
}

private void rejectDeepOffset(Pageable pageable) {
    if (pageable.getOffset() > MAX_OFFSET) {
        throw BusinessException.badRequest(
                "Page %d skips %d rows, more than the %d allowed. Narrow the request with filters or a smaller page window."
                        .formatted(pageable.getPageNumber(), pageable.getOffset(), MAX_OFFSET));
    }
}
```

- Without the allow-list, `?sort=nope` throws `PropertyReferenceException`, so a bad request comes
  back as a 500.
- `MAX_OFFSET` caps how deep the offset may go. `OFFSET 1000000` still reads and discards a million
  rows, and no index changes that.
- The 400 asks the caller to narrow the request — a filter, or a page window nearer the front. A
  human never pages to 50000; a scraper does.
- Every sortable property needs an index. These three live in
  [`V11__add_product_sort_indexes.sql`](../../../project2/src/main/resources/db/changelog/changes/V11__add_product_sort_indexes.sql):

```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_created_at_id ON products (created_at DESC, id DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_name_id ON products (name, id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_price_id ON products (price, id);
```

`GET /api/v1/catalog/products?sort=description` →

```json
{
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "Cannot sort products by [description]. Sortable properties: [createdAt, id, name, price]",
  "path": "/api/v1/catalog/products",
  "timestamp": "2026-09-12T19:31:04.11"
}
```

`GET /api/v1/catalog/products?page=50000&size=20` →

```json
{
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "Page 50000 skips 1000000 rows, more than the 10000 allowed. Narrow the request with filters or a smaller page window.",
  "path": "/api/v1/catalog/products",
  "timestamp": "2026-09-12T19:33:47.902"
}
```

## Slice and cursor-based pagination

[`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java):

```java
@Query("""
        select new com.example.project2.dto.response.ProductSummaryResponse(
            p.id, p.name, p.brand, p.price, p.stockQuantity)
        from Product p
        where :afterId is null or p.id > :afterId
        order by p.id
        """)
Slice<ProductSummaryResponse> findSummariesAfterId(@Param("afterId") Long afterId, Pageable pageable);
```

- The cursor here is `afterId`: a plain row id, not an opaque token. The client sends back the
  `nextAfterId` it was handed. Remembering where you stopped is what keeps this off `OFFSET`.
- `order by p.id` is fixed, so this endpoint ignores `?sort=`. A cursor is only stable against the
  order it was issued for; encode `(sortKey, id)` into one token to cursor over any other sort.
- A `Slice` fetches `size + 1` rows and trims one. That is how `hasNext()` works without a count.
- `Pageable.ofSize(batch)` is always page 0, so the offset stays 0 however far the user scrolls.

`GET /api/v1/catalog/products/scroll?afterId=1200&size=2` →

```json
{
  "success": true,
  "status": 200,
  "data": {
    "content": [
      { "id": 1201, "name": "Desk Lamp", "brand": "Acme", "price": 39.00, "stockQuantity": 120 },
      { "id": 1202, "name": "Desk Mat", "brand": "Acme", "price": 19.00, "stockQuantity": 340 }
    ],
    "hasNext": true,
    "nextAfterId": 1202
  }
}
```

## Comparison

| Aspect | `Page<T>` | `Slice<T>` | `List<T>` |
|---|---|---|---|
| What it does | rows + total count | rows + `hasNext` | rows only |
| Queries per call | 2 | 1 (fetches size + 1) | 1 |
| Client can show | "page 3 of 812" | "load more" | nothing |
| Cost at 1M rows | the count is the slow half | cheap | cheap |
| Failure mode | count drifts between two page requests | no jump-to-page | silently unbounded if you forget `Pageable` |
| Use when | admin table with a pager | endless scroll, mobile feed | export job, small reference table |

Offset paging suits a pager. Keyset suits a feed and stays fast at page 50000.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| No tiebreak in the sort | Rows sharing a `created_at` swap places between requests, so one product shows up on two pages and another on none. |
| Passing the client's `sort` straight through | Unknown property means a 500; unindexed property means a full sort of the table. |
| `Page` + `join fetch` on a collection | Hibernate would page in memory. This module sets `hibernate.query.fail_on_pagination_over_collection_fetch=true`, so it throws instead. |
| Deep offsets | `OFFSET 1000000` reads and discards a million rows on every request. Cap the offset and make the caller narrow the query. |
| Returning `PageImpl` as JSON | Its shape is not a published contract, and it logs a warning on every call. |
| Counting on every keystroke | The count query is often slower than the page itself. Drop it, or cache it per filter. |
| Sorting by a `@Transient` field | `effectivePrice` has no column, so it can only be sorted in memory, after paging. |

## Follow-up questions

**Can I sort by a joined property?** `?sort=category.name` works if the query joins it. Allow-list
it too.

**How do I page a native query?** Add a `Pageable` and your own `countQuery`, and write the
`order by` yourself.

**What does `@EnableSpringDataWebSupport` add?** The `Pageable` and `Sort` argument resolvers.
Boot registers it for you.

**What if the client sends `page=-1`?** The resolver clamps it to 0.

**Is keyset pagination the same as cursor pagination?** Same technique. "Cursor" names what the
client holds and sends back; "keyset" names the `where` predicate that consumes it.

**How do you page a Cassandra table?** With the paging state cursor, never an offset.

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am spring-boot:run

curl "localhost:8088/api/v1/catalog/products?page=0&size=5&sort=price,desc&sort=id,asc"   # multi-sort
curl "localhost:8088/api/v1/catalog/products?size=5000"                                   # clamped to 100
curl "localhost:8088/api/v1/catalog/products?sort=description"                            # 400, not 500
curl "localhost:8088/api/v1/catalog/products?page=50000&size=20"                          # 400, offset over MAX_OFFSET
curl "localhost:8088/api/v1/catalog/products/scroll?size=5"                               # cursor scroll, then follow nextAfterId
```

## References

- [Spring Data JPA — paging and sorting](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html#repositories.special-parameters)
- [Spring Data — web support](https://docs.spring.io/spring-data/jpa/reference/repositories/core-extensions.html#core.web)
- [Spring Boot — `spring.data.web` properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#appendix.application-properties.data)
- Related, same topic folder: [03-global-exception-handling.md](./03-global-exception-handling.md)
- Related, other topic folder: [spring-data-jpa/02-jpql-criteria-native-queries.md](../spring-data-jpa/02-jpql-criteria-native-queries.md), [spring-boot/03-n-plus-one-query-problem.md](../spring-boot/03-n-plus-one-query-problem.md)
