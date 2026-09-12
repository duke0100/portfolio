# What is the difference between first-level and second-level cache in Hibernate?

Applied in [project2](../../../project2) (JPA/Hibernate + PostgreSQL, `Category` reference data).
Every snippet below is real code - the link above each block opens the file it came from, and each
of those files carries an `Interview topic:` back-link to the section here.

| Section | Code in project2 |
|---|---|
| [Answer](#answer) | [`pom.xml`](../../../project2/pom.xml) |
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties), [`ehcache.xml`](../../../project2/src/main/resources/ehcache.xml) |
| [First-level cache](#first-level-cache) | [`CacheDemoServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CacheDemoServiceImpl.java) |
| [Second-level cache](#second-level-cache) | [`Category.java`](../../../project2/src/main/java/com/example/project2/entity/Category.java) |
| [Measuring it](#measuring-it) | [`CacheDemoServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CacheDemoServiceImpl.java), [`CacheDemoController.java`](../../../project2/src/main/java/com/example/project2/controller/CacheDemoController.java), [`CacheDemoReportResponse.java`](../../../project2/src/main/java/com/example/project2/dto/response/CacheDemoReportResponse.java), [`CategoryRepositoryCacheDataJpaTest.java`](../../../project2/src/test/java/com/example/project2/repository/CategoryRepositoryCacheDataJpaTest.java) |

## Answer

L1 is the persistence context: every entity an `EntityManager` has loaded or saved in the current
transaction, kept so that asking for the same id twice returns the exact same Java object without
a second query. It always exists, needs no config, and dies with the transaction. L2 is optional,
shared across every transaction and every user, and sits in front of the database as a
key-value store keyed by entity id - it survives past the transaction that populated it, which is
also what makes it dangerous: two different transactions can now see the same cached row, so it
only belongs on data that changes rarely and doesn't need to be perfectly fresh. Hibernate has no
built-in L2 implementation; it delegates to a JCache provider, here Ehcache 3.

[`project2/pom.xml`](../../../project2/pom.xml):

```xml
<!-- Second-level cache (JCache SPI + Ehcache 3 provider) -->
<!-- see docs/interview/spring-data-jpa/01-first-level-vs-second-level-cache.md#answer -->
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-jcache</artifactId>
</dependency>
<!-- "jakarta" classifier is mandatory: without it, Ehcache 3 ships the old javax.cache API,
     which is binary-incompatible with hibernate-jcache on Hibernate ORM 7's jakarta.* stack. -->
<dependency>
    <groupId>org.ehcache</groupId>
    <artifactId>ehcache</artifactId>
    <classifier>jakarta</classifier>
</dependency>
```

| Cache | Scope | On by default | Needs a provider |
|---|---|---|---|
| L1 (persistence context) | one `EntityManager` / transaction | yes, always | no - built into Hibernate |
| L2 (region cache) | one `SessionFactory`, all transactions | no | yes - JCache (here: Ehcache 3) |

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
spring.jpa.properties.hibernate.cache.use_second_level_cache=true
spring.jpa.properties.hibernate.cache.region.factory_class=org.hibernate.cache.jcache.JCacheRegionFactory
spring.jpa.properties.hibernate.javax.cache.uri=ehcache.xml
```

| Choice | vs the alternative |
|---|---|
| `use_second_level_cache=true` | vs Hibernate's default (`false`): L2 is opt-in - without this, `@Cache` on an entity is silently ignored |
| `region.factory_class=...JCacheRegionFactory` | vs a vendor-specific factory (e.g. Hazelcast's own): JCache (JSR-107) keeps the entity code portable across cache providers |
| `javax.cache.uri=ehcache.xml` | Hibernate resolves this as a plain classpath resource name, not a URI with a `classpath:` scheme - a `classpath:ehcache.xml` value throws `CacheException: Couldn't load URI` at boot |

[`ehcache.xml`](../../../project2/src/main/resources/ehcache.xml):

```xml
<cache alias="categoryCache">
    <expiry>
        <ttl unit="minutes">30</ttl>
    </expiry>
    <resources>
        <heap unit="entries">500</heap>
    </resources>
</cache>
```

- The `alias` must match the `region` attribute on the entity's `@Cache` annotation - a typo here
  just means Hibernate's `create-warn` default silently builds an unbounded cache instead of this one.
- A TTL is the real difference from L1: L1 never goes stale within a transaction because it *is*
  that transaction's view; L2 needs an expiry policy because it outlives the write that could make
  it wrong.

## First-level cache

[`CacheDemoServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CacheDemoServiceImpl.java)
— both loads share one transaction, so the second one never leaves the JVM:

```java
Category first = entityManager.find(Category.class, categoryId);
long afterFirst = statistics.getPrepareStatementCount();

Category repeat = entityManager.find(Category.class, categoryId);
long afterRepeat = statistics.getPrepareStatementCount();
```

- `afterRepeat - afterFirst` is `0`: no SQL, no L2 lookup either - Hibernate returns the managed
  instance straight out of the persistence context.
- `first == repeat` is `true` - not just equal data, the exact same object, which is why dirty
  checking works: there's only ever one in-memory copy per id per transaction.

## Second-level cache

[`Category.java`](../../../project2/src/main/java/com/example/project2/entity/Category.java):

```java
@Entity
@Table(name = "categories")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "categoryCache")
public class Category {
```

- `@Cacheable` (`jakarta.persistence`) is the JPA-portable switch; `@Cache` (Hibernate-specific)
  is where the concurrency strategy and region name actually live - you need both.
- Loading this entity in a later transaction, after it was cached by an earlier one, skips the
  database entirely - see the strategy comparison below for what "later" has to mean here.

## Measuring it

[`CacheDemoServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/CacheDemoServiceImpl.java)
— two separate `REQUIRES_NEW` transactions, not one method plus `entityManager.clear()`:

```java
long statementsBeforeFirstLoad = statistics.getPrepareStatementCount();
FirstTransactionResult firstTx = newTransaction.execute(status -> loadTwiceInOneTransaction(categoryId));
long statementsAfterL1Repeat = statistics.getPrepareStatementCount();

// A brand-new transaction - the only way to actually exercise the L2 read path.
long statementsBeforeL2Load = statistics.getPrepareStatementCount();
Category afterNewTransaction = newTransaction.execute(
        status -> entityManager.find(Category.class, categoryId));
long statementsAfterL2Load = statistics.getPrepareStatementCount();
```

- Hibernate's read-write strategy stamps every cached entry with its writing transaction's own
  timestamp, and refuses to read that entry back inside that same transaction (see the pitfall
  below). Clearing the persistence context only empties L1 - the second load has to happen in a
  genuinely new transaction to actually reach L2, so the demo builds one with `TransactionTemplate`.
- `PROPAGATION_REQUIRES_NEW` is used instead of calling a second `@Transactional` method on `this`,
  because a self-invoked call never goes through the Spring proxy that makes `@Transactional` work.

`GET /api/v1/performance/cache-demo?categoryId=1` →

```json
{
  "success": true,
  "data": {
    "categoryId": 1,
    "sqlStatementsFirstLoad": 1,
    "sqlStatementsL1Repeat": 0,
    "sqlStatementsAfterClearL2Hit": 0,
    "sameInstanceWithinSession": true,
    "sameInstanceAfterClear": false,
    "secondLevelCacheHitCount": 1,
    "secondLevelCacheMissCount": 1
  }
}
```

Proven with H2, no Postgres needed, in
[`CategoryRepositoryCacheDataJpaTest.java`](../../../project2/src/test/java/com/example/project2/repository/CategoryRepositoryCacheDataJpaTest.java):

```java
@Test
void findById_inANewTransaction_hitsL2AndFiresNoSql() {
    long hitsBefore = statistics.getSecondLevelCacheHitCount();

    Category first = categoryRepository.findById(categoryId).orElseThrow(); // L2 miss, then populates L2

    TestTransaction.flagForCommit();
    TestTransaction.end();
    TestTransaction.start(); // a later transaction - only now is the L2 entry readable

    long statementsBeforeSecond = statistics.getPrepareStatementCount();
    Category second = categoryRepository.findById(categoryId).orElseThrow();
    long statementsAfterSecond = statistics.getPrepareStatementCount();

    assertThat(statementsAfterSecond - statementsBeforeSecond).isEqualTo(0); // served from L2, no SQL
    assertThat(statistics.getSecondLevelCacheHitCount() - hitsBefore).isEqualTo(1);
    assertThat(first).isNotSameAs(second); // a new instance, rebuilt from the cached state
}
```

- The sibling test in the same file, `findById_repeatedInSameTransaction_...`, asserts the L1
  behaviour above the same way: statement counts, not timing.
- `first` and `second` hold equal data but are different objects - proof the second load actually
  rebuilt the entity from the cache's serialized state instead of returning the first instance.

## Comparison

| Aspect | L1 (persistence context) | L2 (region cache) |
|---|---|---|
| What it does | de-duplicates entities within one `EntityManager` | de-duplicates database reads across transactions |
| Scope | one transaction | one `SessionFactory`, every transaction |
| On by default | yes | no - needs a provider and `@Cache` |
| Visibility of a fresh write | immediate, same transaction | only from a *later* transaction - never the one that wrote it |
| Failure mode | none - it's just object identity | stale reads if written outside Hibernate (a raw SQL `UPDATE`, another service) |
| Use when | always (can't be turned off) | read-mostly reference data: categories, price lists, feature flags |

Rule of thumb: L1 is what makes a single request's object graph consistent; L2 is a read-through
cache you opt specific entities into, and only when they can tolerate being slightly stale.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| Expecting `entityManager.clear()` to prove an L2 hit | it only empties L1 - the read-write strategy still rejects the L2 entry because it was written by *this same* transaction, so the load falls through to the database and looks like L2 isn't working at all |
| Caching an entity that's also written outside Hibernate | a raw SQL `UPDATE` or a different service's write never touches the cache, so L2 keeps serving the old row until its TTL expires |
| Forgetting the `jakarta` classifier on Ehcache 3 | pulls in the old `javax.cache` API, which throws a confusing `ClassCastException` at startup once `hibernate-jcache` tries to use it |
| Using `@Cache` without `@Cacheable` (or vice versa) | JPA's portable switch and Hibernate's concurrency-strategy annotation are two different things - you need both, or caching silently doesn't happen |
| Caching a frequently-written entity like `Order` | every write pays for a cache lock/unlock and an eventual eviction, for a read that would've hit the database anyway |

## Follow-up questions

**Does L2 cache collections too?** Yes, but the collection itself only stores the ids of its
elements, not the elements' full state - those are looked up from L2 (or the database)
individually. A collection region needs its own `@Cache` on the association.

**What does `NONSTRICT_READ_WRITE` buy you over `READ_WRITE`?** Less locking, and a real risk: a
short window where a stale value can be served after a concurrent write. Use it for data where an
occasional stale read is harmless, like a "last viewed" counter.

**Why does the second-level cache need a JCache provider at all?** Hibernate only defines the
`RegionFactory` SPI; JCache (JSR-107) is the industry-standard cache API it delegates to, so any
compliant provider - Ehcache, Hazelcast, Infinispan - works without changing entity code.

**Does the second-level cache fix the N+1 problem?** Only on a cache hit for each parent's
association - the code still asks once per row either way. See
[03-n-plus-one-query-problem.md](../spring-boot/03-n-plus-one-query-problem.md#follow-up-questions).

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am spring-boot:run

curl -X POST http://localhost:8088/api/v1/categories -H "Content-Type: application/json" \
     -d '{"name":"Peripherals","slug":"peripherals"}'     # note the returned id

curl "http://localhost:8088/api/v1/performance/cache-demo?categoryId=1"   # swap in the real id
```

No database needed for the measurement:

```bash
mvn -pl project2 -am test -Dtest=CategoryRepositoryCacheDataJpaTest    # H2, 2 statement-count assertions
```

## References

- [Hibernate ORM 7 — Caching](https://docs.jboss.org/hibernate/orm/7.0/userguide/html_single/Hibernate_User_Guide.html#caching)
- [Hibernate `AbstractReadWriteAccess`](https://github.com/hibernate/hibernate-orm/blob/main/hibernate-core/src/main/java/org/hibernate/cache/spi/support/AbstractReadWriteAccess.java)
  — "items created after the start of this transaction" are not readable
- [Ehcache 3 — JSR-107 (JCache) provider](https://www.ehcache.org/documentation/3.10/107.html)
- Related, other topic folder: [03-n-plus-one-query-problem.md](../spring-boot/03-n-plus-one-query-problem.md)
  (Hibernate `Statistics`, same measurement technique)
