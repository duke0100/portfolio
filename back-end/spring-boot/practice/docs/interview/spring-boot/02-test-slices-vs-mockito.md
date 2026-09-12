# What's the difference between @SpringBootTest, @WebMvcTest, @DataJpaTest, and @Mock/@InjectMocks?

Applied in [project2](../../../project2). Every snippet links to its source file, and every touched
file carries an `Interview topic:` back-link to the section here.

| Section | Code in project2 |
|---|---|
| [Config](#config) | [`application.properties`](../../../project2/src/test/resources/application.properties) |
| [@SpringBootTest](#springboottest) | [`ProductApiIntegrationTest`](../../../project2/src/test/java/com/example/project2/ProductApiIntegrationTest.java) |
| [@WebMvcTest](#webmvctest) | [`ProductControllerWebMvcTest`](../../../project2/src/test/java/com/example/project2/controller/ProductControllerWebMvcTest.java) |
| [@DataJpaTest](#datajpatest) | [`ProductRepositoryDataJpaTest`](../../../project2/src/test/java/com/example/project2/repository/ProductRepositoryDataJpaTest.java) |
| [@Mock and @InjectMocks](#mock-and-injectmocks) | [`ProductServiceImplTest`](../../../project2/src/test/java/com/example/project2/service/impl/ProductServiceImplTest.java) |

## Answer

`@SpringBootTest` boots the **entire** application — real controller, service, repository, DB —
for end-to-end round trips. `@WebMvcTest` and `@DataJpaTest` are **slices**: only the web layer or
only the JPA layer comes up, everything else is mocked or skipped. `@Mock`/`@InjectMocks` skip
Spring entirely — it's just a plain object graph built by Mockito.
`spring-boot-starter-test` already bundles JUnit 5, Mockito, AssertJ and Spring Test — the only
extra dependencies added here are `h2` and `spring-boot-h2console` (test-scope, in
[`project2/pom.xml`](../../../project2/pom.xml)).

## Config

[`application.properties`](../../../project2/src/test/resources/application.properties)
(`src/test/resources` — used only by tests, never by `spring-boot:run`):

```properties
spring.datasource.url=jdbc:h2:mem:project2_test;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.liquibase.enabled=false
spring.jpa.hibernate.ddl-auto=create-drop
```

This file only matters **for `@SpringBootTest`** — without it, the full-context test would try to
connect to the real Postgres on `localhost:5432` and fail to start. `@DataJpaTest` doesn't need
it: it always swaps in its own embedded H2 automatically (`@AutoConfigureTestDatabase`, default
`Replace.ANY`).

### Browsing the embedded H2 during a test

Setting `spring.h2.console.enabled=true` (in the same file) serves the H2 web console at
`/h2-console` — but only if `spring-boot-h2console` is on the classpath, since Boot 4.0 moved the
console out of the core auto-configuration into its own module. Without that dependency,
`/h2-console` just silently 404s. To actually use it:

1. Set the debugger breakpoint to **Suspend: Thread**, not **All** — an all-threads breakpoint also
   freezes Tomcat's worker threads, so the console request just hangs forever.
2. Open the **application** port (`@LocalServerPort`, or the first "Tomcat started" log line), not
   the second Tomcat instance used for the actuator management server.
3. Log in with the exact JDBC URL `jdbc:h2:mem:project2_test` (user `sa`, empty password) — any
   other URL silently opens a brand new, empty in-memory database with no tables in it.

## @SpringBootTest

[`ProductApiIntegrationTest.java`](../../../project2/src/test/java/com/example/project2/ProductApiIntegrationTest.java):

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductApiIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ProductRepository productRepository; // real bean, real (H2) DB
}
```

Real controller, real service, real repository — nothing is mocked here. That's why the module has
exactly one test like this, rather than one per branch: it's for proving the whole chain works
end-to-end, not for covering every edge case.

`POST /api/v1/products` → `{ "success": true, "data": { "id": 1, "sku": "MON-4K-001" } }`

## @WebMvcTest

[`ProductControllerWebMvcTest.java`](../../../project2/src/test/java/com/example/project2/controller/ProductControllerWebMvcTest.java):

```java
@WebMvcTest(ProductController.class)
class ProductControllerWebMvcTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProductService productService; // no service/repository bean exists here
}
```

Only the web layer starts up — `ProductController`, MVC dispatch, and common-lib's
`@ControllerAdvice` (picked up automatically via component scan, no `@Import` needed here).
`ProductService` has to be a `@MockitoBean`, or the context fails to start because there's no real
implementation of it in this slice.

That auto-detection of `@ControllerAdvice` only works because
[`Project2Application`](../../../project2/src/main/java/com/example/project2/Project2Application.java)
declares `@SpringBootApplication(scanBasePackages = {"com.example.project2", "com.example.commonlib"})`.
Here's why: `@WebMvcTest` disables full auto-configuration and only wires up a small,
framework-provided set of beans for the MVC slice — it never picks up a shared library's own
`@ControllerAdvice`/`@Configuration` classes that way, no matter how that library registers
itself. The only thing that can find them is component scanning, and Spring Boot's default
component scan never looks outside the `@SpringBootApplication` class's own package. That's why
`scanBasePackages` has to name `com.example.commonlib` explicitly — without it, `GlobalExceptionHandler`
would be invisible to every test slice and to the full application alike.

`GET /api/v1/products/99` (mock throws not-found) → `{ "success": false, "error": "NOT_FOUND" }`

## @DataJpaTest

[`ProductRepositoryDataJpaTest.java`](../../../project2/src/test/java/com/example/project2/repository/ProductRepositoryDataJpaTest.java):

```java
@DataJpaTest
class ProductRepositoryDataJpaTest {
    @Autowired private TestEntityManager entityManager;
    @Autowired private ProductRepository productRepository; // real repo, DataSource auto-swapped for H2
}
```

Only the JPA slice starts — no controller, no service, no web layer. Each `@Test` runs inside its
own transaction that's automatically rolled back afterwards, so nothing ever actually gets
committed.

`productRepository.findBySku("KB-001")` after a flush → `Optional[Product(id=1, ...)]`

## @Mock and @InjectMocks

[`ProductServiceImplTest.java`](../../../project2/src/test/java/com/example/project2/service/impl/ProductServiceImplTest.java):

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {
    @Mock private ProductRepository productRepository;
    @InjectMocks private ProductServiceImpl productService; // no Spring context, no DataSource
}
```

`@InjectMocks` builds the real `ProductServiceImpl` by hand and passes in every `@Mock` — no
Spring context, no `MockMvc`, no database at all. This is the fastest of the four styles, and it
can easily assert on specific interactions (like "the miss counter, not the hit counter, was
incremented") that would be awkward to check with a slower test slice.

`productService.findById(99L)` with the repository mock returning empty → throws `BusinessException`

## Comparison

| Aspect | `@SpringBootTest` | `@WebMvcTest` | `@DataJpaTest` | `@Mock`/`@InjectMocks` |
|---|---|---|---|---|
| Context | Full application | Web slice | JPA slice | None |
| Real beans | Everything | Controller, `@ControllerAdvice` | Repository, `EntityManager` | Only the class under test |
| Combo annotation | `@Autowired` only — every collaborator is a real bean, nothing to mock | `@Autowired` (`MockMvc`) + `@MockitoBean` for `ProductService`, the one bean this slice doesn't provide | `@Autowired` (repository, `EntityManager`) — `@MockitoBean` only if something outside the JPA slice is needed | `@Mock` + `@InjectMocks` — no `@Autowired`, no Spring at all |
| Touches real DB? | **Yes**, unless overridden (see [Config](#config)) | Never — no repository bean exists at all | Never — auto-replaced with H2 | Never — there's no `DataSource` to touch |
| Speed | Slowest | Fast | Fast | Fastest |
| Rollback | Whatever the code does | N/A | Auto, per test | N/A |
| Use for | One end-to-end round trip | Controller status/validation/JSON | Repository queries | Every service branch |

`@MockitoBean` is Spring Boot 3.4+/4's rename of the older `@MockBean` — same purpose, it replaces
a real bean in the context with a Mockito mock.

Rule of thumb: pick the **cheapest** style that can still observe what you're actually asserting.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| Using `@MockitoBean` in a slice when `@Mock`+`@InjectMocks` would do | Pays the cost of starting a context for logic that never needed a container in the first place |
| Assuming `@WebMvcTest` loads `@Service`/`@Repository` beans | It only loads the web layer — forgetting `@MockitoBean` fails context startup |
| `@SpringBootTest` hitting the real Postgres by accident | Without the H2 override in [Config](#config), it tries `localhost:5432` and fails before any assertion even runs |
| Assuming a shared library's `@ControllerAdvice`/`@Configuration` registers itself automatically | It doesn't, in any of the three apps, unless the app's `*Application` class explicitly lists the library's package in `scanBasePackages`. Component scan never leaves the `@SpringBootApplication` class's own package by default, and `@WebMvcTest` disables auto-configuration entirely, so a shared jar has no other way to opt in |

## Issues hit building this (Boot 4.0.3 migration)

None of these are hypothetical — each one actually broke compilation or a test run while these
four test classes were written, in this order:

| # | Symptom | Root cause | Fix |
|---|---|---|---|
| 1 | Compile errors like "class `TestRestTemplate`/`DataJpaTest`/`WebMvcTest` not found" | Boot 4.0 split `spring-boot-starter-test` apart — it now only bundles JUnit/AssertJ/Mockito/`@SpringBootTest`, none of the per-technology test slices | Added `spring-boot-starter-webmvc-test` (brings `@WebMvcTest` + `TestRestTemplate`) and `spring-boot-starter-data-jpa-test` (brings `@DataJpaTest` + `TestEntityManager`) as test-scope deps in [`project2/pom.xml`](../../../project2/pom.xml); updated the imports to their new packages |
| 2 | `NoSuchBeanDefinitionException` for the classic Jackson `ObjectMapper` in `@WebMvcTest` | Boot 4.0.3 auto-configures **Jackson 3** by default, not classic Jackson 2 — the old `ObjectMapper` type was only on the classpath transitively, never actually registered as a bean | Migrated both test files to the Jackson 3 types; removed the now-unused Jackson 2 dependencies from `pom.xml` after confirming nothing else used them |
| 3 | `@SpringBootTest` failed to start with a placeholder resolution error for `spring.application.name` | The test-only `application.properties` fully replaces the main one on the test classpath, so a `@Value("${spring.application.name}")` elsewhere had nothing to resolve against | Added `spring.application.name=project2-postgresql` to the test `application.properties`, matching the main one |
| 4 | `GlobalExceptionHandler` never converted a thrown exception into an `ApiError` response — it just bubbled up uncaught | common-lib's beans weren't in scan reach of any app (see the [@WebMvcTest](#webmvctest) section above) | Added `scanBasePackages` to all three `*Application` classes |
| 5 | After fixing #3 and #4, `@SpringBootTest(RANDOM_PORT)` still failed with no `TestRestTemplate` bean found | Boot 4.0.3 no longer auto-registers `TestRestTemplate` for a random-port test; it needs the explicit `@AutoConfigureTestRestTemplate` annotation, plus `RestTemplateBuilder` from a separate module | Added `@AutoConfigureTestRestTemplate` to the test class, and a test-scope `spring-boot-starter-restclient` dependency |
| 6 | `/h2-console` returned a 404 even with `spring.h2.console.enabled=true` | Boot 4.0 moved the H2 console out of core auto-configuration into its own module — the property does nothing without it | Added the test-scope `spring-boot-h2console` dependency |

## Follow-up questions

**Does `@WebMvcTest` load `@ControllerAdvice`?** Yes, but only if it's within the component-scan
reach configured on the app's `@SpringBootApplication` class — see [@WebMvcTest](#webmvctest)
above. It's not part of any web-layer auto-configuration; in fact `@WebMvcTest` disables
auto-configuration entirely and only imports a small, fixed set of framework beans. Component
scanning, filtered down to controller-shaped beans, is the only thing making it work.

**Can `@DataJpaTest` run against the real database?** Yes, with
`@AutoConfigureTestDatabase(replace = Replace.NONE)` to keep the configured datasource — not used
here, since this module's schema is Postgres-specific.

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am test -Dtest=ProductServiceImplTest,ProductRepositoryDataJpaTest,ProductControllerWebMvcTest,ProductApiIntegrationTest
```

## References

- [Spring Boot Testing reference](https://docs.spring.io/spring-boot/reference/testing/index.html)
- [Mockito JUnit 5 extension](https://javadoc.io/doc/org.mockito/mockito-junit-jupiter/latest/org/mockito/junit/jupiter/MockitoExtension.html)
