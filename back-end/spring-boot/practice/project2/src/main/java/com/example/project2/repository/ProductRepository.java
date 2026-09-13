package com.example.project2.repository;

import com.example.project2.dto.response.ProductSearchResultResponse;
import com.example.project2.dto.response.ProductSummaryResponse;
import com.example.project2.entity.Product;
import com.example.project2.repository.projection.ProductSearchProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#jpql
 * Holds the JPQL and the native version of the same product search. The Criteria version lives in
 * {@link ProductSearchRepository}, which this interface also extends.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, ProductSearchRepository {

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#fixing-the-product-listing
     * Overrides the default {@code findAll} to fetch each product's category in the same query,
     * fixing the N+1 for every caller without changing any call site.
     */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Pageable pageable);

    /**
     * Interview topic: docs/interview/rest-api/05-pagination-and-sorting.md#paged-endpoint
     * A page of the catalog list. Spring Data appends the Pageable sort as "order by p.<property>",
     * so the client controls the ORDER BY and this query text never mentions one.
     *
     * <p>The countQuery is written out by hand because Hibernate cannot derive a count from a
     * "select new ..." query.
     */
    @Query(value = """
            select new com.example.project2.dto.response.ProductSummaryResponse(
                p.id, p.name, p.brand, p.price, p.stockQuantity)
            from Product p
            """,
            countQuery = "select count(p) from Product p")
    Page<ProductSummaryResponse> findSummaries(Pageable pageable);

    /**
     * Interview topic: docs/interview/rest-api/05-pagination-and-sorting.md#slice-and-cursor-based-pagination
     * Keyset paging: the client sends the last id it saw instead of a page number, so the database
     * seeks straight into the primary key instead of counting rows it then throws away.
     *
     * <p>Returning a Slice means no count query runs, which is the expensive half of a Page over
     * ~1M rows. Call it with an unsorted Pageable.ofSize(n); the order by here is the cursor order.
     */
    @Query("""
            select new com.example.project2.dto.response.ProductSummaryResponse(
                p.id, p.name, p.brand, p.price, p.stockQuantity)
            from Product p
            where :afterId is null or p.id > :afterId
            order by p.id
            """)
    Slice<ProductSummaryResponse> findSummariesAfterId(@Param("afterId") Long afterId, Pageable pageable);

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#callable-fan-out
     * One of the three queries the snapshot fan-out runs at the same time. Written as JPQL rather
     * than a derived name so the count happens in the database, not in Java.
     */
    @Query("select count(p) from Product p where p.category.id = :categoryId")
    long countByCategory(@Param("categoryId") Long categoryId);

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#callable-fan-out
     * Returns null for a category with no products, which the caller turns into 0.00.
     */
    @Query("select avg(p.price) from Product p where p.category.id = :categoryId")
    Double averagePriceByCategory(@Param("categoryId") Long categoryId);

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#callable-fan-out
     * Bounded by the Pageable, so one category with a million rows still returns a short list.
     */
    @Query("""
            select new com.example.project2.dto.response.ProductSummaryResponse(
                p.id, p.name, p.brand, p.price, p.stockQuantity)
            from Product p
            where p.category.id = :categoryId
              and p.stockQuantity <= p.lowStockThreshold
            order by p.stockQuantity asc, p.id asc
            """)
    List<ProductSummaryResponse> findLowStockByCategory(@Param("categoryId") Long categoryId, Pageable pageable);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    /**
     * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#jpql
     * The JPQL search. The query text never changes, so every optional filter needs its own
     * {@code :param is null or ...} part. It is written over entity names ({@code Product},
     * {@code p.category}), so the same query runs on PostgreSQL, MySQL or H2.
     */
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
            countQuery = """
                    select count(p) from Product p
                    left join p.category c
                    where (:name is null or lower(p.name) like lower(concat('%', :name, '%')))
                      and (:brand is null or p.brand = :brand)
                      and (:status is null or p.status = :status)
                      and (:categoryId is null or c.id = :categoryId)
                      and (:minPrice is null or p.price >= :minPrice)
                      and (:maxPrice is null or p.price <= :maxPrice)
                      and (:inStock = false or p.stockQuantity > 0)
                    """)
    Page<ProductSearchResultResponse> searchWithJpql(@Param("name") String name,
                                                     @Param("brand") String brand,
                                                     @Param("status") String status,
                                                     @Param("categoryId") Long categoryId,
                                                     @Param("minPrice") BigDecimal minPrice,
                                                     @Param("maxPrice") BigDecimal maxPrice,
                                                     @Param("inStock") boolean inStock,
                                                     Pageable pageable);

    /**
     * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#native-query
     * PostgreSQL only. {@code to_tsvector} and {@code plainto_tsquery} match word stems, so
     * "wireless mice" also finds "wireless mouse", and {@code ts_rank} sorts by best match. JPQL
     * cannot say any of this. It needs the index from {@code V7__add_product_search_indexes.sql};
     * without it the database reads all ~1M rows.
     *
     * <p>Use {@code cast(:brand as varchar)}, not {@code ::varchar}: without the cast the driver
     * cannot guess the parameter type, and {@code ::} confuses the named-parameter parser.
     */
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
            countQuery = """
                    select count(*)
                    from products p
                    where to_tsvector('english', p.name || ' ' || coalesce(p.description, ''))
                          @@ plainto_tsquery('english', :text)
                      and (cast(:brand as varchar) is null or p.brand = cast(:brand as varchar))
                      and (cast(:minPrice as numeric) is null or p.price >= cast(:minPrice as numeric))
                    """,
            nativeQuery = true)
    Page<ProductSearchProjection> searchWithFullText(@Param("text") String text,
                                                     @Param("brand") String brand,
                                                     @Param("minPrice") BigDecimal minPrice,
                                                     Pageable pageable);
}
