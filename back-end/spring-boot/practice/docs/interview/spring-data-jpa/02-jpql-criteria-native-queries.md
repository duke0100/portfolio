# Explain the difference between JPQL, Criteria API and native queries. When do you choose each?

Applied in [project2](../../../project2) (JPA/Hibernate + PostgreSQL). The `products` table there
holds about 1M rows, so the index and the ranking below really matter.
Every snippet is real code - the link above each block opens the file it came from, and each of
those files links back to the section here.

| Section | Code in project2 |
|---|---|
| [Answer](#answer) | [`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java) |
| [Config](#config) | [`pom.xml`](../../../project2/pom.xml), [`application.properties`](../../../project2/src/main/resources/application.properties), [`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties) |
| [JPQL](#jpql) | [`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java), [`ProductSearchResultResponse.java`](../../../project2/src/main/java/com/example/project2/dto/response/ProductSearchResultResponse.java) |
| [Criteria API](#criteria-api) | [`ProductSearchRepositoryImpl.java`](../../../project2/src/main/java/com/example/project2/repository/impl/ProductSearchRepositoryImpl.java), [`ProductSearchRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductSearchRepository.java), [`ProductSearchRequest.java`](../../../project2/src/main/java/com/example/project2/dto/request/ProductSearchRequest.java) |
| [Native query](#native-query) | [`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java), [`ProductSearchProjection.java`](../../../project2/src/main/java/com/example/project2/repository/projection/ProductSearchProjection.java), [`V7__add_product_search_indexes.sql`](../../../project2/src/main/resources/db/changelog/changes/V7__add_product_search_indexes.sql) |
| [Running all three](#running-all-three) | [`ProductSearchService.java`](../../../project2/src/main/java/com/example/project2/service/ProductSearchService.java), [`ProductSearchServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductSearchServiceImpl.java), [`ProductSearchController.java`](../../../project2/src/main/java/com/example/project2/controller/ProductSearchController.java), [`ProductSearchReportResponse.java`](../../../project2/src/main/java/com/example/project2/dto/response/ProductSearchReportResponse.java) |
| [Comparison](#comparison) | [`ProductSearchRepositoryDataJpaTest.java`](../../../project2/src/test/java/com/example/project2/repository/ProductSearchRepositoryDataJpaTest.java) |

## Answer

All three read the same data. The difference is what you write.

**JPQL** is a query over your Java classes, not over tables. You write `Product` and `p.category`,
and Hibernate turns it into SQL for your database. So the same query runs on PostgreSQL, MySQL or
H2.

**Criteria API** builds the same query with Java code instead of one long text. Because it is code,
you can add a filter only when the user sent it. It also uses generated classes like
`Product_.price`, so a wrong field name is a compile error.

**A native query** is plain SQL. You get everything your database can do, but the query works only
on that database.

In practice I start with a Spring Data method name or JPQL. I move to Criteria (or a
`Specification`, which is Criteria inside) as soon as the filters are optional - that is any search
screen. I use native SQL only when JPQL cannot say it, like PostgreSQL full-text search.

[`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java)
- all three styles sit behind one repository:

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, ProductSearchRepository {
```

| Style | You write | Filters chosen | Runs on any DB | Can return |
|---|---|---|---|---|
| JPQL / HQL | text over classes | when you write the query | yes | entities, DTOs, single values |
| Criteria API | Java code | while the app runs | yes | entities, DTOs, single values |
| Native SQL | text over tables | when you write the query | no | projections, single values, entities |

## Config

[`project2/pom.xml`](../../../project2/pom.xml) - these generated classes are what make Criteria
type-safe:

```xml
<annotationProcessorPaths>
    <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </path>
    <!-- Builds the metamodel classes (Product_, Category_, ...) so the Criteria
         search is checked by the compiler instead of written as text.
         see docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#criteria-api -->
    <path>
        <groupId>org.hibernate.orm</groupId>
        <artifactId>hibernate-jpamodelgen</artifactId>
        <!-- The version has to be written here: this list does not read the Spring
             Boot BOM, so the build fails without it. -->
        <version>${hibernate.version}</version>
    </path>
</annotationProcessorPaths>
```

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.generate_statistics=true
```

| Choice | Why not the other way |
|---|---|
| `hibernate-jpamodelgen` | With `root.get("price")` a renamed field breaks when the app runs. With `Product_.price` it breaks in the compiler. |
| `<version>${hibernate.version}</version>` | `annotationProcessorPaths` does not read the Boot BOM. Leave the version out and the build fails with `Cannot find version for annotation processor path`. |
| `generate_statistics=true` | This is where the search report gets its SQL count from. It costs a little speed, so keep it only if you read the numbers. |
| `show-sql=false` | Production stays quiet. The dev profile below turns it on. |

[`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties)
- how to see the generated SQL on your machine:

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
```

## JPQL

[`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java)
- class names, and a DTO built by `select new`:

```java
@Query(value = """
        select new com.example.project2.dto.response.ProductSearchResultResponse(
            p.id, p.name, p.brand, p.price, c.name)
        from Product p
        left join p.category c
        where (:name is null or lower(p.name) like lower(concat('%', :name, '%')))
          and (:brand is null or p.brand = :brand)
          and (:status is null or p.status = :status)
          and (:categoryId is null or c.id = :categoryId)
          and (:minPrice is null or p.price >= :minPrice)
          and (:maxPrice is null or p.price <= :maxPrice)
          and (:inStock = false or p.stockQuantity > 0)
        """,
        countQuery = """ ... same where clause ... """)
Page<ProductSearchResultResponse> searchWithJpql(@Param("name") String name, ...);
```

- The query text never changes, so each optional filter needs its own `:param is null or ...` part.
  Every filter is sent on every call, even the ones nobody filled in.
- `left join p.category c` uses the mapping, so there is no `on` clause to get wrong. An inner join
  here would quietly hide products that have no category.

Generated SQL, taken from the `@DataJpaTest` run on H2:

<!-- not in this repo: Hibernate output, not a source file -->
```sql
select p1_0.id,p1_0.name,p1_0.brand,p1_0.price,c1_0.name
from products p1_0 left join categories c1_0 on c1_0.id=p1_0.category_id
where (? is null or lower(p1_0.name) like lower(('%'||?||'%')) escape '')
  and (? is null or p1_0.brand=?) and (? is null or p1_0.status=?)
  and (? is null or c1_0.id=?) and (? is null or p1_0.price>=?)
  and (? is null or p1_0.price<=?) and (?=false or p1_0.stock_quantity>0)
order by p1_0.id fetch first ? rows only
```

## Criteria API

[`ProductSearchRepositoryImpl.java`](../../../project2/src/main/java/com/example/project2/repository/impl/ProductSearchRepositoryImpl.java)
- an empty field simply adds no condition:

```java
private Predicate[] toPredicates(CriteriaBuilder cb, Root<Product> product, ProductSearchRequest request) {
    List<Predicate> predicates = new ArrayList<>();

    if (StringUtils.hasText(request.getName())) {
        predicates.add(cb.like(cb.lower(product.get(Product_.name)),
                "%" + request.getName().toLowerCase() + "%"));
    }
    if (request.getCategoryId() != null) {
        // Reading the id through the field, not through the join, so the count query can reuse this.
        predicates.add(cb.equal(product.get(Product_.category).get(Category_.id), request.getCategoryId()));
    }
    if (Boolean.TRUE.equals(request.getInStock())) {
        predicates.add(cb.greaterThan(product.get(Product_.stockQuantity), 0));
    }

    return predicates.toArray(new Predicate[0]);
}
```

[`ProductSearchRepositoryImpl.java`](../../../project2/src/main/java/com/example/project2/repository/impl/ProductSearchRepositoryImpl.java)
- the select part, and the paging you have to write yourself:

```java
query.select(cb.construct(ProductSearchResultResponse.class,
                product.get(Product_.id),
                product.get(Product_.name),
                product.get(Product_.brand),
                product.get(Product_.price),
                category.get(Category_.name)))
        .where(toPredicates(cb, product, request))
        .orderBy(cb.asc(product.get(Product_.id)));

List<ProductSearchResultResponse> rows = entityManager.createQuery(query)
        .setFirstResult((int) pageable.getOffset())
        .setMaxResults(pageable.getPageSize())
        .getResultList();

// No count query is needed when the first page already tells us the total.
return PageableExecutionUtils.getPage(rows, pageable, () -> count(cb, request));
```

- `cb.construct` is the Criteria version of JPQL's `select new`. Same DTO, and no entity is loaded.
- Paging and counting are your job. That is the real cost of Criteria, and one `Root` cannot be
  shared by the data query and the count query.

Generated SQL for `?name=mouse&brand=Acme&inStock=true`. The four filters that stayed empty left no
trace at all:

<!-- not in this repo: Hibernate output, not a source file -->
```sql
select p1_0.id,p1_0.name,p1_0.brand,p1_0.price,c1_0.name
from products p1_0 left join categories c1_0 on c1_0.id=p1_0.category_id
where lower(p1_0.name) like ? escape '' and p1_0.brand=? and p1_0.stock_quantity>?
order by 1 offset ? rows fetch first ? rows only
```

[`ProductSearchRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductSearchRepository.java)
- the small interface `ProductRepository` extends, so callers never see an `EntityManager`:

```java
public interface ProductSearchRepository {

    Page<ProductSearchResultResponse> searchWithCriteria(ProductSearchRequest request, Pageable pageable);
}
```

- Spring Data finds the code by name. The class must be called `ProductSearchRepositoryImpl`, or
  nothing is wired.

## Native query

[`ProductRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductRepository.java)
- PostgreSQL full-text search, which JPQL cannot express at all:

```java
@Query(value = """
        select p.id            as id,
               p.name          as name,
               p.brand         as brand,
               p.price         as price,
               c.name          as category_name,
               ts_rank(to_tsvector('english', p.name || ' ' || coalesce(p.description, '')),
                       plainto_tsquery('english', :text)) as search_rank
        from products p
        left join categories c on c.id = p.category_id
        where to_tsvector('english', p.name || ' ' || coalesce(p.description, ''))
              @@ plainto_tsquery('english', :text)
          and (cast(:brand as varchar) is null or p.brand = cast(:brand as varchar))
          and (cast(:minPrice as numeric) is null or p.price >= cast(:minPrice as numeric))
        order by search_rank desc, p.id
        """,
        countQuery = """ ... same where clause ... """,
        nativeQuery = true)
Page<ProductSearchProjection> searchWithFullText(@Param("text") String text, ...);
```

- `plainto_tsquery` matches word stems, so "wireless mice" also finds "wireless mouse", and
  `ts_rank` sorts by best match. `like '%...%'` can do neither.
- Write `cast(:brand as varchar)`, not `::varchar`. Without the cast the driver cannot guess the
  parameter type, and `::` confuses the named-parameter parser.

[`ProductSearchProjection.java`](../../../project2/src/main/java/com/example/project2/repository/projection/ProductSearchProjection.java)
- native rows come back as a projection, not as entities:

```java
public interface ProductSearchProjection {

    Long getId();

    String getCategoryName();

    Double getSearchRank();
}
```

- Getter names must match the SQL aliases. Spring Data also reads snake_case, so
  `as category_name` fills `getCategoryName()`.

[`V7__add_product_search_indexes.sql`](../../../project2/src/main/resources/db/changelog/changes/V7__add_product_search_indexes.sql)
- the query is only fast with the right index:

```sql
--changeset system:V7-001 runInTransaction:false dbms:postgresql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_fulltext
    ON products
    USING GIN (to_tsvector('english', name || ' ' || coalesce(description, '')));
```

- The expression in the index must match the `where` clause exactly. If it does not, the database
  ignores the index and reads all ~1M rows.
- `CONCURRENTLY` cannot run inside a transaction, so the changeset sets `runInTransaction:false`.

## Running all three

[`ProductSearchServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductSearchServiceImpl.java):

```java
Page<ProductSearchResultResponse> page = switch (style) {
    case JPQL -> jpql(request, pageable);
    case CRITERIA -> productRepository.searchWithCriteria(request, pageable);
    case NATIVE -> nativeFullText(request, pageable);
};
```

[`ProductSearchServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductSearchServiceImpl.java)
- the native call needs one small change the other two do not:

```java
Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
return productRepository.searchWithFullText(
                request.getText(), emptyToNull(request.getBrand()), request.getMinPrice(), unsorted)
        .map(this::toResult);
```

- The sort is dropped on purpose. Spring Data would add its own `order by` and break the query's
  `order by search_rank desc`.
- An empty parameter becomes `null` first. `""` is not `null`, so `:name is null` would never be
  true and JPQL would filter on an empty string.

`GET /api/v1/product-search?style=CRITERIA&name=mouse&brand=Acme&inStock=true`

```json
{
  "success": true,
  "status": 200,
  "message": "Success",
  "data": {
    "style": "CRITERIA",
    "note": "Type-safe and dynamic; only the filters you send reach the SQL",
    "skipsUnusedFilters": true,
    "rows": 1,
    "totalElements": 1,
    "sqlStatements": 1,
    "products": [
      { "id": 1, "name": "Wireless Mouse", "brand": "Acme", "price": 25.00,
        "categoryName": "Peripherals", "searchRank": null }
    ]
  }
}
```

`GET /api/v1/product-search/compare?text=wireless%20mouse&brand=Acme` runs every style on the same
filters. `NATIVE` is skipped when `text` is missing, because `plainto_tsquery` would have nothing to
match.

## Comparison

| Aspect | JPQL | Criteria API | Native SQL |
|---|---|---|---|
| What it is | a query over your classes, read at startup | the same query, built in Java code | your database's own SQL |
| Use it when | you always filter on the same fields | the filters are optional or come from the user | JPQL cannot say it, or your SQL is faster |
| Speed | same SQL as Criteria for the same filters, but the unused `is null` parts can push the database to a bad index | best SQL for the filters really used | whatever you write, DB functions included |
| Runs on any DB | yes - the `@DataJpaTest` proves it on H2 | yes | no: `to_tsvector` and the GIN index are PostgreSQL only |
| How it breaks | a wrong class or field name stops the app at startup | long builder code, and you own the paging | a renamed column is invisible to the compiler and breaks when the query runs |
| Can return | entities, `select new` DTOs, single values | entities, `cb.construct` DTOs, single values | projections and `Tuple`; an entity only if you select every mapped column |

Rule of thumb: a Spring Data method name first, then JPQL, then Criteria once the filters become
optional, and native only when the database offers something JPA cannot say.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| Many `:param is null or ...` filters in one query | One saved plan has to serve every filter mix, so on a big table the database can pick a bad index |
| A `Pageable` with a `Sort` on a native query | Spring Data adds its own `order by` and fights yours - pass a `PageRequest` with no sort |
| A native query typed to return `Product` without all mapped columns | Hibernate cannot build the entity and fails when the query runs |
| Using one Criteria `Root` for the count query too | JPA does not allow a shared root; build a second query with its own |
| A fetch join together with paging | Hibernate has to page in memory. project2 makes this an error with `hibernate.query.fail_on_pagination_over_collection_fetch=true` |
| Passing empty strings as filters | `""` is not `null`, so the "skip this filter" part never runs |
| `::cast` inside a native `@Query` | It clashes with named parameters - use `cast(x as type)` |
| Building native SQL by joining strings | SQL injection; bind the values with `@Param` |

## Follow-up questions

**Where do Spring Data `Specification`s fit?** A `Specification` is a small lambda that returns one
Criteria condition, and `JpaSpecificationExecutor` gives you paging and counting for free. Use the
raw `CriteriaBuilder`, like
[`ProductSearchRepositoryImpl`](../../../project2/src/main/java/com/example/project2/repository/impl/ProductSearchRepositoryImpl.java)
does, only when you need full control of the select part.

**Are JPQL queries checked at startup?** Yes. Hibernate reads every `@Query` while the app starts,
so a wrong field name stops the app. A native query is only text until the database sees it.

**Does a native query affect the persistence context?** Hibernate writes pending changes first, but
the rows come back around the first-level cache. A native `update` or `delete` leaves loaded
entities out of date, so add `@Modifying(clearAutomatically = true)`.

**Criteria API or QueryDSL?** QueryDSL reads much better for the same idea, but it needs its own
build plugin. Criteria is plain JPA, so this module uses it.

**How would you page the native search past page 100?** Keyset paging on `(search_rank, id)`. With
`offset 2000` PostgreSQL still ranks and sorts every matching row, then throws most of them away.

## Try it

```bash
cd back-end/spring-boot/practice

# The JPQL and Criteria searches run on H2, no database needed - that is the "any DB" claim.
mvn -pl project2 test -Dtest=ProductSearchRepositoryDataJpaTest

# The full app needs the local Postgres; Liquibase creates V7's index at startup.
mvn -pl project2 -am spring-boot:run -Dspring-boot.run.profiles=dev

curl "http://localhost:8088/api/v1/product-search?style=CRITERIA&name=mouse&inStock=true"   # no is-null parts in the logged SQL
curl "http://localhost:8088/api/v1/product-search?style=JPQL&name=mouse&inStock=true"       # same rows, seven is-null parts
curl "http://localhost:8088/api/v1/product-search/compare?text=wireless%20mouse"            # the NATIVE row carries searchRank
```

## References

- [Hibernate Query Language guide](https://docs.jboss.org/hibernate/orm/7.0/querylanguage/html_single/Hibernate_Query_Language.html) - official docs first
- [Spring Data JPA - query methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html)
- [PostgreSQL - controlling text search](https://www.postgresql.org/docs/current/textsearch-controls.html)
- Related, same topic folder: [First-level vs second-level cache](./01-first-level-vs-second-level-cache.md)
- Related, other topic folder: [N+1 query problem](../spring-boot/03-n-plus-one-query-problem.md), [Database migrations](../database/01-database-migrations.md)
