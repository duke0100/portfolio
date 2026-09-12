# Explain the common JPA mapping annotations: @ManyToOne, @OneToMany, @JoinColumn, mappedBy, @JoinTable, @Id, @GeneratedValue, @Enumerated and @Transient

Applied in [project2](../../../project2) (JPA/Hibernate + PostgreSQL + Liquibase, where the schema
is written by hand so you can see exactly which column each annotation maps).
Every snippet is real code - the link above each block opens the file it came from, and each of
those files links back to the section here.

| Section | Code in project2 |
|---|---|
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties), [`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties) |
| [Owning side](#owning-side) | [`ProductReview.java`](../../../project2/src/main/java/com/example/project2/entity/ProductReview.java), [`V8__create_product_reviews.sql`](../../../project2/src/main/resources/db/changelog/changes/V8__create_product_reviews.sql) |
| [@JoinColumn attributes](#joincolumn-attributes) | [`ProductReview.java`](../../../project2/src/main/java/com/example/project2/entity/ProductReview.java) |
| [Inverse side with mappedBy](#inverse-side-with-mappedby) | [`Product.java`](../../../project2/src/main/java/com/example/project2/entity/Product.java), [`Category.java`](../../../project2/src/main/java/com/example/project2/entity/Category.java) |
| [Read-only @JoinColumn](#read-only-joincolumn) | [`Category.java`](../../../project2/src/main/java/com/example/project2/entity/Category.java), [`CategoryTreeResponse.java`](../../../project2/src/main/java/com/example/project2/dto/response/CategoryTreeResponse.java) |
| [Unidirectional vs bidirectional](#unidirectional-vs-bidirectional) | [`Product.java`](../../../project2/src/main/java/com/example/project2/entity/Product.java) |
| [@JoinTable many-to-many](#jointable-many-to-many) | [`Product.java`](../../../project2/src/main/java/com/example/project2/entity/Product.java), [`Tag.java`](../../../project2/src/main/java/com/example/project2/entity/Tag.java), [`TagRepository.java`](../../../project2/src/main/java/com/example/project2/repository/TagRepository.java), [`V9__create_tags_and_product_tags.sql`](../../../project2/src/main/resources/db/changelog/changes/V9__create_tags_and_product_tags.sql), [`AddProductTagsRequest.java`](../../../project2/src/main/java/com/example/project2/dto/request/AddProductTagsRequest.java), [`ProductTagsResponse.java`](../../../project2/src/main/java/com/example/project2/dto/response/ProductTagsResponse.java) |
| [@Id and @GeneratedValue](#id-and-generatedvalue) | [`Tag.java`](../../../project2/src/main/java/com/example/project2/entity/Tag.java), [`ProductReview.java`](../../../project2/src/main/java/com/example/project2/entity/ProductReview.java) |
| [@Enumerated and @Transient](#enumerated-and-transient) | [`ReviewStatus.java`](../../../project2/src/main/java/com/example/project2/entity/ReviewStatus.java), [`ProductReview.java`](../../../project2/src/main/java/com/example/project2/entity/ProductReview.java), [`Product.java`](../../../project2/src/main/java/com/example/project2/entity/Product.java) |
| [Syncing both sides](#syncing-both-sides) | [`Product.java`](../../../project2/src/main/java/com/example/project2/entity/Product.java), [`Order.java`](../../../project2/src/main/java/com/example/project2/entity/Order.java), [`RelationshipServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/RelationshipServiceImpl.java), [`CreateProductReviewRequest.java`](../../../project2/src/main/java/com/example/project2/dto/request/CreateProductReviewRequest.java) |
| [Reading the collection](#reading-the-collection) | [`ProductReviewRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductReviewRepository.java), [`ProductReviewResponse.java`](../../../project2/src/main/java/com/example/project2/dto/response/ProductReviewResponse.java) |
| [equals, hashCode and toString](#equals-hashcode-and-tostring) | [`ProductReview.java`](../../../project2/src/main/java/com/example/project2/entity/ProductReview.java) |
| [Seeing it fail](#seeing-it-fail) | [`RelationshipService.java`](../../../project2/src/main/java/com/example/project2/service/RelationshipService.java), [`RelationshipServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/RelationshipServiceImpl.java), [`RelationshipController.java`](../../../project2/src/main/java/com/example/project2/controller/RelationshipController.java), [`RelationshipDemoReportResponse.java`](../../../project2/src/main/java/com/example/project2/dto/response/RelationshipDemoReportResponse.java) |

## Answer

In a one-to-many the FK column can only live in one place: the child table. So `@ManyToOne` plus
`@JoinColumn` is the owning side, and that is the mapping Hibernate reads when it writes the column.

`@OneToMany(mappedBy = "product")` is the same column seen from the parent. `mappedBy` names the
child field that owns it and means "no column on this side". Drop it and JPA creates a join table
instead.

So writing to the collection alone changes nothing in the database. Set the child's `@ManyToOne`,
and keep the collection in sync with a helper method.

| Annotation | Goes on | Effect on the schema | Default fetch |
|---|---|---|---|
| `@ManyToOne` | the child field pointing at the parent | FK column on this table | `EAGER` |
| `@OneToMany(mappedBy)` | the parent's collection | nothing | `LAZY` |
| `@OneToMany` + `@JoinColumn` | the parent's collection | FK on the child, written from the parent | `LAZY` |
| `@OneToMany` with neither | the parent's collection | extra join table | `LAZY` |
| `@ManyToMany` + `@JoinTable` | either side | join table of the two FKs | `LAZY` |
| `@JoinColumn` | either owning side | names/tunes the FK column | - |
| `@JoinTable` | the owning side of a many-to-many | the link table itself | - |

[`V8__create_product_reviews.sql`](../../../project2/src/main/resources/db/changelog/changes/V8__create_product_reviews.sql)
- the table the mappings below describe:

```sql
CREATE TABLE product_reviews (
    id           BIGSERIAL                NOT NULL,
    product_id   BIGINT                   NOT NULL,
    author_name  VARCHAR(150)             NOT NULL,
    rating       INT                      NOT NULL,
    status       VARCHAR(20)              NOT NULL DEFAULT 'PENDING',
    CONSTRAINT pk_product_reviews         PRIMARY KEY (id),
    CONSTRAINT fk_product_reviews_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_product_reviews_rating  CHECK (rating BETWEEN 1 AND 5)
);
```

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.default_batch_fetch_size=50
spring.jpa.properties.hibernate.generate_statistics=true
```

| Choice | vs the alternative |
|---|---|
| `open-in-view=false` | vs `true`: a lazy association touched outside the service transaction throws instead of quietly running a query during JSON serialisation |
| `default_batch_fetch_size=50` | vs `-1`: a missed fetch join costs `ceil(N/50)` selects instead of `N` - a safety net, not a fix |
| `generate_statistics=true` | vs `false`: without it the statement counts on `/demo` are all zero |

[`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties)
- the loose local settings, on purpose:

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=true
spring.jpa.properties.hibernate.default_batch_fetch_size=-1
```

- Run the `dev` profile to see the SQL. A stray `update product_reviews set product_id = ?` right
  after the insert means both sides are writing the FK.

## Owning side

[`ProductReview.java`](../../../project2/src/main/java/com/example/project2/entity/ProductReview.java):

```java
@Entity
@Table(name = "product_reviews")
public class ProductReview {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            referencedColumnName = "id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_reviews_product"))
    private Product product;
```

- `fetch = LAZY` is spelled out because `@ManyToOne` defaults to `EAGER`; on the default, reading
  one review also loads the whole product. `optional = false` promises the FK is never null, so
  Hibernate can use an inner join.

## @JoinColumn attributes

| Attribute | What it does | Leave it out when |
|---|---|---|
| `name` | the FK column name | you accept the default `product_id` (field + `_` + parent PK) |
| `referencedColumnName` | which parent column the FK points at | it is the parent's PK, which is the default |
| `nullable` | `NOT NULL` in generated DDL, and lets Hibernate pick an inner join | the FK really is optional |
| `insertable` / `updatable` | set both `false` to make the association read-only | this mapping should write the column |
| `foreignKey` | names the constraint in generated DDL; `@ForeignKey(NO_CONSTRAINT)` skips it | you manage DDL yourself and do not care about the name |
| `unique` | adds a unique constraint, turning many-to-one into one-to-one | duplicates are allowed |

- `nullable = false` is about the column and `optional = false` is about the query Hibernate
  builds, so set both. `referencedColumnName` can point at any unique column, e.g. `products.sku`.

## Inverse side with mappedBy

[`Product.java`](../../../project2/src/main/java/com/example/project2/entity/Product.java):

```java
@OneToMany(
        mappedBy = "product",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY)
@Builder.Default
private List<ProductReview> reviews = new ArrayList<>();
```

[`Category.java`](../../../project2/src/main/java/com/example/project2/entity/Category.java)
- the same idea, self-referencing, and deliberately without cascade:

```java
@OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
@Builder.Default
private List<Category> children = new ArrayList<>();
```

| Setting | What it does | Use it when |
|---|---|---|
| `cascade = PERSIST, MERGE` | saving the parent saves new children | children are created through the parent |
| `cascade = REMOVE` | deleting the parent deletes the children | children are meaningless alone |
| `cascade = ALL` | all of the above | a true parent/child aggregate, like reviews |
| `orphanRemoval = true` | removing a child from the list deletes its row | the list is the only owner of the child |
| no cascade | children saved and deleted through their own repository | children are shared or have their own lifecycle |

- `cascade = REMOVE` deletes the children one row at a time. The FK's `ON DELETE CASCADE` does it
  in one statement, but then Hibernate's cache still thinks they exist. A product has a handful of
  reviews, so the row-by-row delete is fine here.
- Categories get no cascade: deleting a category must never silently delete its subtree.

## Read-only @JoinColumn

[`Category.java`](../../../project2/src/main/java/com/example/project2/entity/Category.java)
- `parent_id` was already mapped as a plain `Long`, so the association has to be read-only:

```java
@Column(name = "parent_id")
private Long parentId;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_id", insertable = false, updatable = false)
private Category parent;
```

- Two writable mappings of one column and Hibernate refuses to start
  (`Column 'parent_id' is duplicated`), so one of them has to be read-only. Callers keep writing
  `parentId`, which is why `CategoryServiceImpl` needed no change.

`GET /api/v1/relationships/categories/12/tree` →

```json
{
  "status": 200,
  "data": {
    "id": 12,
    "name": "Laptops",
    "parentId": 3,
    "parentName": "Computers",
    "childNames": ["Gaming laptops", "Ultrabooks"],
    "childCount": 2
  }
}
```

## Unidirectional vs bidirectional

[`Product.java`](../../../project2/src/main/java/com/example/project2/entity/Product.java)
- `Category` has no `products` list, and that is a design decision:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id")
private Category category;
```

- With ~1M products, one `getProducts()` would pull the table into the heap - query
  `ProductRepository` by category id instead. Add the inverse side only when you navigate that
  way, or want cascade from the parent.

<!-- not in this repo -->

```java
// No mappedBy: JPA cannot tell this is the same link as ProductReview.product, so it invents
// product_reviews_join(product_id, reviews_id) and leaves the real FK unused.
@OneToMany
private List<ProductReview> reviews;

// @JoinColumn instead: no join table, but the parent writes the child's FK, so the child is
// inserted with product_id null and updated straight after.
@OneToMany
@JoinColumn(name = "product_id")
private List<ProductReview> reviews;
```

| Mapping | Tables | Statements per new child | Notes |
|---|---|---|---|
| `@ManyToOne` + `@OneToMany(mappedBy)` | 2 | 1 insert | what you want |
| `@OneToMany` + `@JoinColumn` (unidirectional) | 2 | insert + update | child has no reference back to the parent |
| `@OneToMany` with neither | 3 | insert + join-table insert | almost always a mapping bug |

## @JoinTable many-to-many

A many-to-many is the one case where the join table is right: neither row can hold the other's key.

[`Product.java`](../../../project2/src/main/java/com/example/project2/entity/Product.java)
- `joinColumns` points back at this entity, `inverseJoinColumns` at the other one:

```java
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
        name = "product_tags",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"),
        foreignKey = @ForeignKey(name = "fk_product_tags_product"),
        inverseForeignKey = @ForeignKey(name = "fk_product_tags_tag"))
@Builder.Default
private Set<Tag> tags = new LinkedHashSet<>();
```

- Swap the two column lists by accident and Hibernate happily writes every row backwards.
- `Tag` has no `products` collection, so there is nothing to keep in sync and no way to load a
  million products from one tag.

[`V9__create_tags_and_product_tags.sql`](../../../project2/src/main/resources/db/changelog/changes/V9__create_tags_and_product_tags.sql):

```sql
CREATE TABLE product_tags (
    product_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    CONSTRAINT pk_product_tags         PRIMARY KEY (product_id, tag_id),
    CONSTRAINT fk_product_tags_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_tags_tag     FOREIGN KEY (tag_id)     REFERENCES tags     (id) ON DELETE CASCADE
);
```

[`RelationshipServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/RelationshipServiceImpl.java)
- the many-to-many has no cascade, so a brand new tag is saved before it is attached:

```java
Tag tag = tagRepository.findByNameIgnoreCase(trimmed)
        .orElseGet(() -> tagRepository.save(Tag.builder().name(trimmed).build()));
product.getTags().add(tag);
```

`POST /api/v1/relationships/products/1/tags` with `{ "names": ["wireless", "sale"] }` →

```json
{
  "status": 200,
  "data": {
    "productId": 1,
    "productName": "Product 1",
    "effectivePrice": 89.10,
    "tagNames": ["sale", "wireless"],
    "tagCount": 2
  }
}
```

| Question | Answer |
|---|---|
| When do you need `@JoinTable`? | many-to-many, or a one-to-many you refuse to give the child an FK for |
| Which side owns it? | the side that declares `@JoinTable`; the other one uses `mappedBy` |
| `joinColumns` | FK back to the entity holding the annotation |
| `inverseJoinColumns` | FK to the entity on the other end |
| Better alternative? | map the join table as an entity as soon as it needs a column of its own |

`OrderDetail` in this module is exactly that alternative: `orders` and `products` are many-to-many
in spirit, but the link carries `quantity` and `unit_price`, so it is an entity rather than a
`@JoinTable`.

## Syncing both sides

[`Product.java`](../../../project2/src/main/java/com/example/project2/entity/Product.java)
- one call that sets both ends, so no caller can get it half right:

```java
public void addReview(ProductReview review) {
    reviews.add(review);
    review.setProduct(this);
}

/** Detaching the review here is what makes orphanRemoval delete the row on flush. */
public void removeReview(ProductReview review) {
    reviews.remove(review);
    review.setProduct(null);
}
```

[`Order.java`](../../../project2/src/main/java/com/example/project2/entity/Order.java)
- the same helpers where the collection has no cascade, so they are in-memory only:

```java
public void addDetail(OrderDetail detail) {
    details.add(detail);
    detail.setOrder(this);
}
```

- Skipping the sync does not always fail loudly. The insert can succeed and the stale collection
  only shows up later, as a total that is one review short.

[`RelationshipServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/RelationshipServiceImpl.java)
- the write path saves the parent, and cascade inserts the child:

```java
product.addReview(review);
productRepository.saveAndFlush(product);
```

`POST /api/v1/relationships/products/1/reviews`

```json
{ "authorName": "ada", "rating": 5, "title": "Solid", "comment": "Two weeks in, no complaints." }
```

→

```json
{
  "status": 201,
  "data": { "id": 41, "productId": 1, "productName": "Product 1", "authorName": "ada", "rating": 5 }
}
```

## Reading the collection

[`ProductReviewRepository.java`](../../../project2/src/main/java/com/example/project2/repository/ProductReviewRepository.java):

```java
List<ProductReview> findByProductIdOrderByIdAsc(Long productId);

@Query("""
        select distinct r
        from ProductReview r
        left join fetch r.product p
        where p.id = :productId
        """)
List<ProductReview> findWithProductByProductId(@Param("productId") Long productId);
```

- Querying the child by `productId` beats walking `getReviews()`: same rows, no parent entity, and
  you can paginate it.
- `review.getProduct().getId()` on a lazy proxy is free - the id is the FK Hibernate already has.
  Any other field triggers the select.

`GET /api/v1/relationships/products/1/reviews?fetchProduct=true` returns a list of the body shown
above. With `fetchProduct=false`, `productName` comes back null and one query is saved.

## equals, hashCode and toString

[`ProductReview.java`](../../../project2/src/main/java/com/example/project2/entity/ProductReview.java):

```java
@Override
public boolean equals(Object other) {
    if (this == other) {
        return true;
    }
    if (!(other instanceof ProductReview that)) {
        return false;
    }
    return id != null && id.equals(that.id);
}

@Override
public int hashCode() {
    return getClass().hashCode();
}
```

- `hashCode` is constant per class so the object keeps its bucket after Hibernate assigns the id.
  An id-based hash changes on insert, and the entity is then lost inside any `HashSet`.
- Neither method touches `product`. Lombok's `@Data` or a generated `toString` walks parent to
  child to parent and blows the stack, so these entities only use `@Getter`/`@Setter`.

## Seeing it fail

[`RelationshipController.java`](../../../project2/src/main/java/com/example/project2/controller/RelationshipController.java)
- one scenario per call, and the transaction is always rolled back:

```java
@GetMapping("/demo")
public ApiResponse<RelationshipDemoReportResponse> runScenario(
        @RequestParam RelationshipService.Scenario scenario,
        @RequestParam Long productId) {
    return ApiResponse.ok(relationshipService.runScenario(scenario, productId));
}
```

[`RelationshipServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/RelationshipServiceImpl.java):

```java
/** The row is written, but nobody updates the parent's list for us. */
private String owningSideOnly(Product product, ProductReview review) {
    review.setProduct(product);
    productReviewRepository.saveAndFlush(review);
    return "Set review.product and saved the review. Row written, product.reviews stale.";
}

/** Cascade inserts the review, but nothing ever fills in product_id. */
private String inverseSideOnly(Product product, ProductReview review) {
    product.getReviews().add(review);
    entityManager.flush();
    return "Added to product.reviews without setting review.product.";
}
```

| `?scenario=` | What it does | Result |
|---|---|---|
| `OWNING_SIDE_ONLY` | sets `review.product`, saves the review | row written, `product.reviews` still stale |
| `INVERSE_SIDE_ONLY` | adds to `product.reviews` only | `FAILED` - insert with `product_id` null |
| `BOTH_SIDES_SYNCED` | `product.addReview(...)` | row written and the list is correct |
| `ORPHAN_REMOVAL` | add, flush, remove from the list | Hibernate issues the delete |

`GET /api/v1/relationships/demo?scenario=INVERSE_SIDE_ONLY&productId=1` →

```json
{
  "status": 200,
  "data": {
    "scenario": "INVERSE_SIDE_ONLY",
    "outcome": "FAILED",
    "error": "PSQLException: ERROR: null value in column \"product_id\" violates not-null constraint",
    "reviewsInDatabase": -1,
    "rolledBack": true
  }
}
```

## @Id and @GeneratedValue

[`ProductReview.java`](../../../project2/src/main/java/com/example/project2/entity/ProductReview.java)
- what every other entity in this module uses:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

[`Tag.java`](../../../project2/src/main/java/com/example/project2/entity/Tag.java)
- a sequence instead, so tag inserts can be batched:

```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tag_seq")
@SequenceGenerator(name = "tag_seq", sequenceName = "tags_seq", allocationSize = 50)
private Long id;
```

- The `allocationSize` must match `INCREMENT BY` on the sequence in
  [`V9__create_tags_and_product_tags.sql`](../../../project2/src/main/resources/db/changelog/changes/V9__create_tags_and_product_tags.sql),
  or both will hand out ids the other already used.

| Strategy | How the id arrives | Batching | Use when |
|---|---|---|---|
| `IDENTITY` | the database assigns it during the insert | no - Hibernate must flush each row to learn the id | simple tables, Postgres/MySQL auto-increment |
| `SEQUENCE` | Hibernate asks the sequence first, 50 ids at a time | yes | high insert volume, Postgres/Oracle |
| `TABLE` | a separate table of counters | yes, but with row locks | portability only, avoid otherwise |
| `AUTO` | provider picks - `SEQUENCE` on Postgres, `IDENTITY` on MySQL | depends | prototypes; be explicit in real code |
| `UUID` | generated in the JVM | yes | ids that must exist before insert, or cross-database merges |

- Assigning the id yourself with a bare `@Id` and no `@GeneratedValue` is also legal, and then
  `save()` on an existing id becomes an update rather than an insert.

## @Enumerated and @Transient

[`ProductReview.java`](../../../project2/src/main/java/com/example/project2/entity/ProductReview.java):

```java
@Enumerated(EnumType.STRING)
@Column(name = "status", length = 20, nullable = false)
@Builder.Default
private ReviewStatus status = ReviewStatus.PENDING;
```

- `ORDINAL` stores 0, 1, 2. Insert a new constant in the middle of the enum and every existing row
  silently means something else, which no migration can detect afterwards.

[`Product.java`](../../../project2/src/main/java/com/example/project2/entity/Product.java)
- `@Transient` keeps a derived value out of the schema:

```java
@Transient
private BigDecimal effectivePrice;

@PostLoad
void computeEffectivePrice() {
    if (price == null) {
        return;
    }
    BigDecimal percent = discountPercent != null ? discountPercent : BigDecimal.ZERO;
    BigDecimal factor = BigDecimal.ONE.subtract(percent.divide(BigDecimal.valueOf(100)));
    this.effectivePrice = price.multiply(factor).setScale(2, RoundingMode.HALF_UP);
}
```

- The field is filled in on load only, so an entity you built with the Lombok builder has it null
  until it comes back from the database.

| Choice | vs the alternative |
|---|---|
| `EnumType.STRING` | vs `ORDINAL`: readable column, and reordering the enum is harmless |
| `@Transient` field | vs a stored column: nothing to migrate when the formula changes, but you cannot filter or sort on it in SQL |
| `@Transient` (JPA) | vs the `transient` keyword: JPA hides it from the database, Java hides it from serialisation |

`PATCH /api/v1/relationships/products/1/reviews/41/status?status=PUBLISHED` writes the string
`PUBLISHED`; anything outside the enum comes back as a 400 before the service is reached.

## Comparison

| Aspect | `@ManyToOne` + `@JoinColumn` (owning) | `@OneToMany(mappedBy)` (inverse) |
|---|---|---|
| What it does | maps the FK column on the child table | mirrors that column as a collection |
| Owns the FK | yes - only this side writes it | no - ignored on write |
| Default fetch | `EAGER`, so set `LAZY` yourself | `LAZY` |
| Performance | one FK per row, cheap to read | unbounded collection; needs a fetch join or `@BatchSize` |
| Failure mode | `EAGER` by default drags the parent into every read | writes look accepted but change nothing |
| Use when | always, for any many-to-one | you navigate parent to children, or want cascade/`orphanRemoval` |

Rule of thumb: map the `@ManyToOne` first and make sure it works. Add the collection only when a
use case needs it.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| Forgetting `mappedBy` | JPA creates a join table and the real FK column sits unused |
| `@OneToMany` + `@JoinColumn` from the parent | every child costs an insert plus an update |
| Both sides mapped as writable | two mappings write the same column: extra updates, and confusing conflicts |
| Adding to the collection without setting the `@ManyToOne` | `product_id` is null and the insert is rejected |
| Leaving `@ManyToOne` at its `EAGER` default | one extra join or select on every single read of the child |
| Reading a lazy collection in a loop | N+1: one select per parent |
| Lombok `@Data` or a generated `toString` on a bidirectional entity | infinite recursion between parent and child |
| `equals`/`hashCode` on a mutable id | the entity changes bucket on insert and vanishes from its `HashSet` |
| `cascade = REMOVE` on a large collection | one delete per child row instead of one statement |
| `orphanRemoval = true` on a shared child | detaching it from one parent deletes a row another parent still needs |
| `joinColumns` and `inverseJoinColumns` swapped | every join-table row is written backwards, and nothing complains |
| `@ManyToMany` on a `List` | removing one element makes Hibernate delete and reinsert the whole set |
| `allocationSize` different from the sequence's `INCREMENT BY` | duplicate key errors as soon as two apps insert |
| `EnumType.ORDINAL` | inserting an enum constant changes the meaning of rows already stored |
| `@Transient` when you meant a column | the value is silently never saved |

## Follow-up questions

**Why is `@ManyToOne` `EAGER` by default?** The spec assumes one FK is cheap to join. It is still
the wrong default in a web app, so write `fetch = FetchType.LAZY` on every `@ManyToOne`.

**Can `mappedBy` and `@JoinColumn` sit on the same field?** No - `mappedBy` says the other side
owns the column, so Hibernate rejects the contradiction.

**`Set` or `List`?** `List` is fine with `mappedBy`. Fetch two `List`s eagerly and you get
`MultipleBagFetchException`; `Set` or `@OrderColumn` fixes that.

**How do you avoid the N+1 on parents with children?** `join fetch`, `@EntityGraph` or
`@BatchSize` - see
[03-n-plus-one-query-problem.md](../spring-boot/03-n-plus-one-query-problem.md).

**What changes for `@OneToOne`?** Same rules. The inverse side cannot be lazy without bytecode
enhancement, so it is often mapped as a `@ManyToOne` instead.

**Does `@ManyToMany` need `mappedBy` too?** Yes: one side declares `@JoinTable`, the other
`mappedBy`. Here only `Product` maps it, so there is no inverse side to keep in sync.

**Why do people say to avoid `@ManyToMany`?** Because the link almost always grows a column later,
and `OrderDetail` in this module shows the answer - map the join table as an entity instead.

**Why does `IDENTITY` stop insert batching?** Hibernate only learns the id from the insert itself,
so it cannot queue rows. `SEQUENCE` with `allocationSize = 50` gets the ids first and batches.

**`@Transient` or the `transient` keyword?** `@Transient` keeps the field out of the database;
the Java keyword keeps it out of serialisation. They are unrelated, and you usually want the
annotation.

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am spring-boot:run -Dspring-boot.run.profiles=dev

# The FK is written from the owning side only - this one fails
curl "http://localhost:8088/api/v1/relationships/demo?scenario=INVERSE_SIDE_ONLY&productId=1"

# Same write done through the helper
curl "http://localhost:8088/api/v1/relationships/demo?scenario=BOTH_SIDES_SYNCED&productId=1"

# orphanRemoval: watch for the delete in the show-sql output
curl "http://localhost:8088/api/v1/relationships/demo?scenario=ORPHAN_REMOVAL&productId=1"

# Real write, then read it back with and without the join fetch
curl -X POST http://localhost:8088/api/v1/relationships/products/1/reviews \
  -H "Content-Type: application/json" \
  -d '{"authorName":"ada","rating":5,"title":"Solid","comment":"No complaints."}'
curl "http://localhost:8088/api/v1/relationships/products/1/reviews?fetchProduct=true"

# Self-referencing association: parent read through the read-only @JoinColumn
curl "http://localhost:8088/api/v1/relationships/categories/1/tree"

# @JoinTable: two rows in product_tags, and effectivePrice comes from the @Transient field
curl -X POST http://localhost:8088/api/v1/relationships/products/1/tags \
  -H "Content-Type: application/json" \
  -d '{"names":["wireless","sale"]}'
curl "http://localhost:8088/api/v1/relationships/products/1/tags"

# @Enumerated: the column holds the text PUBLISHED, not a number
curl -X PATCH "http://localhost:8088/api/v1/relationships/products/1/reviews/1/status?status=PUBLISHED"
```

## References

- [Jakarta Persistence 3.2 - entity relationships](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2#a380)
- [Hibernate User Guide - associations](https://docs.jboss.org/hibernate/orm/7.0/userguide/html_single/Hibernate_User_Guide.html#associations)
- [Spring Data JPA reference](https://docs.spring.io/spring-data/jpa/reference/jpa.html)
- Related, same topic folder: [01-first-level-vs-second-level-cache.md](./01-first-level-vs-second-level-cache.md),
  [02-jpql-criteria-native-queries.md](./02-jpql-criteria-native-queries.md)
- Related, other topic folder: [03-n-plus-one-query-problem.md](../spring-boot/03-n-plus-one-query-problem.md),
  [01-database-migrations.md](../database/01-database-migrations.md)
