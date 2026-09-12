# How do you design a versioned REST API in Spring Boot?

Applied in [project2](../../../project2) (Web + JPA + PostgreSQL; it owns the product catalog, so
v2 has a real payload to break). Each block links the file it came from, and each of those files
back-links to the section here.

| Section | Code in project2 |
|---|---|
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties), [`application-sunset.properties`](../../../project2/src/main/resources/application-sunset.properties) |
| [The breaking change](#the-breaking-change) | [`ProductV2Response.java`](../../../project2/src/main/java/com/example/project2/dto/response/v2/ProductV2Response.java) |
| [URI versioning](#uri-versioning) | [`CatalogProductV1Controller.java`](../../../project2/src/main/java/com/example/project2/controller/version/CatalogProductV1Controller.java), [`CatalogProductV2Controller.java`](../../../project2/src/main/java/com/example/project2/controller/version/CatalogProductV2Controller.java) |
| [Header versioning](#header-versioning) | [`CatalogProductHeaderVersionController.java`](../../../project2/src/main/java/com/example/project2/controller/version/CatalogProductHeaderVersionController.java) |
| [Media type versioning](#media-type-versioning) | [`CatalogProductMediaTypeController.java`](../../../project2/src/main/java/com/example/project2/controller/version/CatalogProductMediaTypeController.java), [`WebConfig.java`](../../../project2/src/main/java/com/example/project2/config/WebConfig.java) |
| [One service, two payloads](#one-service-two-payloads) | [`ProductV2Mapper.java`](../../../project2/src/main/java/com/example/project2/mapper/ProductV2Mapper.java) |
| [Testing the routing](#testing-the-routing) | [`ApiVersioningWebMvcTest.java`](../../../project2/src/test/java/com/example/project2/controller/version/ApiVersioningWebMvcTest.java) |

## Answer

Version the payload, not the business logic. I default to URI versioning: a version in the URL is
one that caches, gateways and support tickets can all see. Header versioning is the cleaner REST
answer, and Spring Framework 7 supports it natively. Vendor media types are proper content
negotiation but the hardest for clients. Whichever I pick, only the DTO is versioned — a version
costs a mapper and a controller, never a second copy of the domain.

| Strategy | Looks like | I reach for it when |
|---|---|---|
| URI | `GET /api/v2/catalog/products/1` | public APIs, browser-testable, CDN in front |
| Request header | `X-API-Version: 2` | one canonical URL per resource, internal clients |
| Vendor media type | `Accept: application/vnd.catalog.v2+json` | strict REST, machine clients, per-representation versioning |
| Query param | `?version=2` | quick experiments; it pollutes the cache key |

No new dependency — `spring-boot-starter-web` already carries the Boot 4 versioning support.

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
# Spring reads the version from the X-API-Version header and matches it against
# @GetMapping(version=...), so one URL can serve both versions.
spring.mvc.apiversion.use.header=X-API-Version
spring.mvc.apiversion.supported=1,2

# No header means v1, so older clients - and springdoc's /v3/api-docs - keep working.
spring.mvc.apiversion.default=1
spring.mvc.apiversion.required=false
```

| Choice | vs the alternative |
|---|---|
| `spring.mvc.apiversion.default=1` | vs leaving it unset: unset plus `required=true` gives every version-less caller a 400 the day you switch versioning on |
| `spring.mvc.apiversion.use.header` | vs `use.path-segment=1`: that resolver parses the segment for every request in the app, so `/api/catalog/...` would fail on `catalog` |
| `spring.mvc.apiversion.supported=1,2` | vs leaving it to `detect-supported`: the explicit list is the contract you can point a client at |
| `required=false` | vs `true`: `true` is honest, but only workable once every client sends the header |

[`application-sunset.properties`](../../../project2/src/main/resources/application-sunset.properties)
— the same knobs, set the way that breaks callers:

```properties
spring.mvc.apiversion.supported=2
spring.mvc.apiversion.default=2
spring.mvc.apiversion.required=true
spring.mvc.apiversion.detect-supported=false
```

- `X-API-Version: 1` now returns 400, and a caller sending no header is silently upgraded to the v2
  shape.

## The breaking change

[`ProductV2Response.java`](../../../project2/src/main/java/com/example/project2/dto/response/v2/ProductV2Response.java):

```java
@Getter
@Builder
public class ProductV2Response {

    private Long id;
    private String name;
    private String sku;
    private String status;
    private Integer stockQuantity;
    private CategoryRef category;
    private Pricing pricing;
    private Dimensions dimensions;
    private LocalDateTime updatedAt;

    public record CategoryRef(Long id, String name) {
    }

    public record Pricing(BigDecimal listPrice,
                          BigDecimal discountPercent,
                          BigDecimal effectivePrice,
                          String currency) {
    }

    public record Dimensions(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
    }
}
```

| v1 field | v2 field | Why a v1 client breaks |
|---|---|---|
| `price`, `discountPercent` | `pricing.listPrice`, `pricing.discountPercent`, `pricing.effectivePrice` | the two fields it read are gone |
| `categoryId`, `categoryName` | `category.id`, `category.name` | flat keys became a nested object |
| `dimensions` (`"30x20x5"`) | `dimensions.lengthCm` / `widthCm` / `heightCm` | a string became an object |

- Adding `pricing` next to `price` would need no version. Only removals and renames do.

## URI versioning

[`CatalogProductV1Controller.java`](../../../project2/src/main/java/com/example/project2/controller/version/CatalogProductV1Controller.java)
and [`CatalogProductV2Controller.java`](../../../project2/src/main/java/com/example/project2/controller/version/CatalogProductV2Controller.java):

```java
@RestController
@RequestMapping("/api/v1/catalog/products")
public class CatalogProductV1Controller {

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(productService.findById(id));
    }
}

@RestController
@RequestMapping("/api/v2/catalog/products")
public class CatalogProductV2Controller {

    @GetMapping("/{id}")
    public ApiResponse<ProductV2Response> findById(@PathVariable Long id) {
        return ApiResponse.ok(productV2Mapper.toV2(productService.findById(id)));
    }
}
```

- Two controllers per resource is the cost; the gateway rule `/api/v2/** -> new pod` is the payoff.

`GET /api/v2/catalog/products/1` →

```json
{
  "success": true,
  "status": 200,
  "data": {
    "id": 1,
    "name": "Mechanical Keyboard",
    "category": { "id": 7, "name": "Keyboards" },
    "pricing": { "listPrice": 100.00, "discountPercent": 10.00, "effectivePrice": 90.00, "currency": "USD" },
    "dimensions": { "lengthCm": 30, "widthCm": 20, "heightCm": 5 }
  }
}
```

## Header versioning

[`CatalogProductHeaderVersionController.java`](../../../project2/src/main/java/com/example/project2/controller/version/CatalogProductHeaderVersionController.java)
— one URL; the `version` attribute of `@GetMapping` (new in Spring Framework 7) does the routing:

```java
@RestController
@RequestMapping("/api/catalog/products")
public class CatalogProductHeaderVersionController {

    @GetMapping(value = "/{id}", version = "1")
    public ApiResponse<ProductResponse> findByIdV1(@PathVariable Long id) {
        return ApiResponse.ok(productService.findById(id));
    }

    @GetMapping(value = "/{id}", version = "2")
    public ApiResponse<ProductV2Response> findByIdV2(@PathVariable Long id) {
        return ApiResponse.ok(productV2Mapper.toV2(productService.findById(id)));
    }
}
```

- `version = "1.2+"` means "1.2 and every supported version above it".
- A method with no `version` matches any version, at the lowest priority. That is why the rest of
  project2 keeps working with versioning on.

```bash
curl -H "X-API-Version: 2" localhost:8088/api/catalog/products/1    # nested pricing
curl                       localhost:8088/api/catalog/products/1    # default=1, flat price
curl -H "X-API-Version: 9" localhost:8088/api/catalog/products/1    # 400, version not supported
```

## Media type versioning

[`CatalogProductMediaTypeController.java`](../../../project2/src/main/java/com/example/project2/controller/version/CatalogProductMediaTypeController.java):

```java
@RestController
@RequestMapping("/api/catalog/products")
public class CatalogProductMediaTypeController {

    public static final String V1_JSON = "application/vnd.catalog.v1+json";
    public static final String V2_JSON = "application/vnd.catalog.v2+json";

    @GetMapping(produces = {V1_JSON, MediaType.APPLICATION_JSON_VALUE})
    public ApiResponse<Page<ProductResponse>> findAllV1(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(productService.findAll(pageable));
    }

    @GetMapping(produces = V2_JSON)
    public ApiResponse<Page<ProductV2Response>> findAllV2(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(productService.findAll(pageable).map(productV2Mapper::toV2));
    }
}
```

- v1 also produces plain `application/json` on purpose, so an old client lands on v1, not an error.
- The Jackson converter writes any `application/*+json` type, so a vendor type needs no converter.

[`WebConfig.java`](../../../project2/src/main/java/com/example/project2/config/WebConfig.java)
— without it, a wildcard Accept matches both vendor types equally well and Spring throws
"Ambiguous handler methods mapped":

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON);
    }
}
```

`GET /api/catalog/products` with `Accept: application/vnd.catalog.v2+json` → `200`,
`Content-Type: application/vnd.catalog.v2+json`. With `Accept: application/xml` → `406`.

## One service, two payloads

[`ProductV2Mapper.java`](../../../project2/src/main/java/com/example/project2/mapper/ProductV2Mapper.java):

```java
@Component
public class ProductV2Mapper {

    public ProductV2Response toV2(ProductResponse v1) {
        return ProductV2Response.builder()
                .id(v1.getId())
                .name(v1.getName())
                .category(v1.getCategoryId() == null ? null
                        : new ProductV2Response.CategoryRef(v1.getCategoryId(), v1.getCategoryName()))
                .pricing(new ProductV2Response.Pricing(
                        v1.getPrice(),
                        v1.getDiscountPercent(),
                        effectivePrice(v1.getPrice(), v1.getDiscountPercent()),
                        CURRENCY))
                .dimensions(parseDimensions(v1.getDimensions()))
                .build();
    }
}
```

- All three controllers call the same `productService.findById(id)`. Fork the service per version
  and you end up with two definitions of "effective price" that disagree.
- Removing v2 means deleting this mapper, its DTO and its controllers — nothing else.

## Testing the routing

[`ApiVersioningWebMvcTest.java`](../../../project2/src/test/java/com/example/project2/controller/version/ApiVersioningWebMvcTest.java)
— one mocked service answer, asserted through each strategy (7 tests, all green):

```java
@WebMvcTest({CatalogProductV1Controller.class, CatalogProductV2Controller.class,
        CatalogProductHeaderVersionController.class, CatalogProductMediaTypeController.class})
@Import({ProductV2Mapper.class, WebConfig.class})
class ApiVersioningWebMvcTest {

    @Test
    void headerVersioning_routesOnXApiVersion() throws Exception {
        when(productService.findById(1L)).thenReturn(PRODUCT);

        mockMvc.perform(get("/api/catalog/products/{id}", 1L).header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(100.00));

        mockMvc.perform(get("/api/catalog/products/{id}", 1L).header("X-API-Version", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricing.effectivePrice").value(90.00));
    }

    @Test
    void headerVersioning_unsupportedVersion_isRejected() throws Exception {
        mockMvc.perform(get("/api/catalog/products/{id}", 1L).header("X-API-Version", "9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mediaTypeVersioning_wildcardAccept_fallsBackToV1AsPlainJson() throws Exception {
        mockMvc.perform(get("/api/catalog/products"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.data.content[0].price").value(100.00));
    }
}
```

- `@WebMvcTest` already picks up `WebConfig`, since it is a `WebMvcConfigurer`; `ProductV2Mapper`
  is what needs the `@Import`.
- The `spring.mvc.apiversion.*` block is repeated in `src/test/resources/application.properties`,
  which shadows the main file on the test classpath.

## Comparison

| Aspect | URI `/api/v2/...` | Header `X-API-Version: 2` | Media type `vnd.catalog.v2+json` |
|---|---|---|---|
| Discoverability | in the URL, visible in logs and tickets | invisible until you read the request | invisible until you read the request |
| Browser and curl friendly | yes, paste and go | needs a flag | needs a flag, and a wrong Accept gives 406 |
| CDN and proxy caching | different URL, so a different cache entry, no config | needs `Vary: X-API-Version` or the cache serves the wrong shape | needs `Vary: Accept`, which most caches already handle |
| Gateway routing | a path rule, trivial | a header rule, supported but less common | a header rule over a parsed media type |
| HATEOAS links | every link must be built for the caller's version | links stay version-free, which is the point | links stay version-free |
| Testability | plain `get("/api/v2/...")` | `.header(...)` | `.accept(...)` |
| REST purity | low: the URI should name the resource, not its format | medium | high |
| Cost in code | one controller per version | one controller, `version` per method | one controller, `produces` per method |

Rule of thumb: URI for public traffic, header behind a gateway, media type when the representation
is what varies.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| Enabling `spring.mvc.apiversion` with no `default` | every request without the header is a 400, including springdoc's own `/v3/api-docs` call, so Swagger UI goes blank |
| Two vendor media types and a client sending a wildcard Accept | both mappings match equally well and Spring throws "Ambiguous handler methods mapped" — a 500, not a 406 |
| Header or media type versioning without `Vary` | a shared cache stores the v2 body and later hands it to a v1 client |
| Versioning the service layer, not just the DTO | the rules get duplicated and drift, and a bug fix lands in only one version |
| Minting a version for an added field | a new optional field is backward compatible; every version you create is one you have to keep alive |
| `use.path-segment` in a mixed URL space | the resolver parses that segment on every request, so any path without a version there fails |
| No sunset plan | v1 lives forever because nobody counted who still calls it — log the resolved version per request from day one |

## Follow-up questions

**Whole API or single endpoints?** The number is API-wide; only changed endpoints get a new
mapping. `version = "1.2+"` covers the unchanged ones.

**How do you deprecate a version?** Announce a date, send `Deprecation` and `Sunset` headers
(Spring's `ApiVersionDeprecationHandler` writes them), count calls per version, delete at zero.

**What about request bodies?** Same rule: a `CreateProductV2Request` plus a mapper into the
existing service input, and the vendor type on `consumes` if you version by media type.

**How do you keep OpenAPI usable?** One springdoc `GroupedOpenApi` per version. Header and media
type schemes also need the header documented on every operation.

**Why not a custom `@ApiVersion` and `RequestCondition`?** That was the pre-Boot-4 answer. The
`version` attribute covers it now; write the condition only for a scheme `ApiVersionParser` cannot
express.

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am spring-boot:run

# URI versioning
curl -s localhost:8088/api/v1/catalog/products/1                        # flat price
curl -s localhost:8088/api/v2/catalog/products/1                        # nested pricing

# Header versioning, one URL
curl -s -H "X-API-Version: 2" localhost:8088/api/catalog/products/1     # v2 payload
curl -s -i -H "X-API-Version: 9" localhost:8088/api/catalog/products/1  # 400

# Media type versioning
curl -s -H "Accept: application/vnd.catalog.v2+json" "localhost:8088/api/catalog/products?size=1"
curl -s -i "localhost:8088/api/catalog/products?size=1"                 # wildcard Accept -> v1 JSON

# What sunsetting v1 does to callers
mvn -pl project2 -am spring-boot:run -Dspring-boot.run.profiles=sunset
curl -s -i -H "X-API-Version: 1" localhost:8088/api/catalog/products/1  # 400

mvn -pl project2 -am test -Dtest=ApiVersioningWebMvcTest -Dsurefire.failIfNoSpecifiedTests=false
```

## References

- [Spring MVC API versioning config](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/api-version.html) — `ApiVersionConfigurer`
- [@RequestMapping version attribute](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html) — matching rules, including `1.2+`
- [RFC 9745 - The Deprecation HTTP Header Field](https://www.rfc-editor.org/rfc/rfc9745.html)
- Related: [Spring Boot Actuator](../spring-boot/01-spring-boot-actuator.md) — same properties file
- Related: [Test slices vs Mockito](../spring-boot/02-test-slices-vs-mockito.md) — why these are `@WebMvcTest`
