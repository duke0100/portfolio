# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A multi-module Maven reactor for practicing/documenting Spring Boot concepts, one real
implementation per topic area rather than toy snippets. Parent POM is
`org.springframework.boot:spring-boot-starter-parent:4.0.3`, Java 25 everywhere.

| Module | Stack | Port | Purpose |
|---|---|---|---|
| `common-lib` | plain Spring library (jar) | — | Shared `ApiResponse`/`ApiError`/`BusinessException`/`GlobalExceptionHandler`/`SwaggerConfig`, reused by all three apps |
| `project1` | Web + JPA/Hibernate + **MySQL** + Liquibase | 8081 | MySQL specifics, Liquibase, JPA basics, transactions/locking |
| `project2` | Web + JPA/Hibernate + **PostgreSQL** + Liquibase + Actuator | 8088 (mgmt 9082) | Performance topics (~1M dummy products), indexing, pagination, N+1, batching, caching, actuator/metrics |
| `project3` | Web + **Spring Data Cassandra** + Liquibase-style keyspace init | 8083 | NoSQL modelling, partition keys, eventual consistency |

All interview-style write-ups live in `docs/interview/<topic-folder>/NN-<slug>.md`, cross-linked
from the code via `Interview topic: docs/interview/...#anchor` comments. `docs/interview/README.md`
is the index. This cross-linking is maintained by the `spring-boot-interview` agent/skill — when
touching code that carries one of these back-link comments, keep the doc and the code in sync.

## Build / run / test

There is **no `mvnw` at the reactor root** — only inside each of `project1`/`project2`/`project3`
(and `interview-coding` is a sibling, unrelated single module outside this reactor). There is also
no system `mvn` on `PATH`. On this machine, build from the reactor root with an explicit toolchain:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
$env:PATH = "C:\Users\WINDOWS 10\.m2\wrapper\dists\apache-maven-3.9.16\56ba1f9f\bin;$env:JAVA_HOME\bin;$env:PATH"
cd D:\workspace\dev-documentation\back-end\spring-boot\practice
mvn -pl project2 -am compile -DskipTests   # -am also builds common-lib, which every module depends on
mvn -pl project2 -am test                   # full module test run
mvn -pl project2 -am test -Dtest=ProductServiceImplTest              # single test class
mvn -pl project2 -am test -Dtest=ProductServiceImplTest#create_...   # single test method
```

Swap `project2` for whichever module you're working in. JaCoCo runs on every `test` phase
(`jacoco-maven-plugin`, `PACKAGE`/`LINE` coverage check, currently a no-op minimum of `0.0`).

Local infra for the real databases (not needed for the test suites, which use H2/embedded fakes):

```bash
cd back-end/spring-boot/practice
docker-compose up -d      # mysql:8.4 (3306), postgres:17 (5434->5432), cassandra:4.1 (9042)
```

## Architecture

### Layering (project1/project2/project3)

`controller/` → `service/` (interface) → `service/impl/` → `repository/` → `entity/`, with
`dto/request/` and `dto/response/` for controller I/O. Constructor injection only — `@RequiredArgsConstructor`
+ `private final` fields throughout; there is no field `@Autowired` anywhere in `src/main`, and new
code should not introduce any. Entities use Lombok (`@Getter @Setter @Builder @NoArgsConstructor
@AllArgsConstructor`, `@Builder.Default` for defaults, `@PrePersist`/`@PreUpdate` for timestamps).

Not every module implements every entity's full stack — e.g. `Category` has a controller/service
only in `project2`; project1/project3 stop at entity+repository+dto for it. Check what already
exists in the target module before assuming a layer is missing.

`project2` additionally has `actuator/` (custom `HealthIndicator`, `InfoContributor`,
`MeterRegistryCustomizer`-style metrics config) and a `repository/projection/` package for
interface-based JPA projections (used by `OrderDetailRepository`).

### common-lib contract

Every controller returns `ApiResponse<T>` on success (`ApiResponse.ok(data)` /
`.created(data)`) and lets `GlobalExceptionHandler` (`@ControllerAdvice extends
ResponseEntityExceptionHandler`) turn exceptions into `ApiError` bodies. Throw
`BusinessException.notFound/badRequest/conflict/forbidden(...)` from services rather than
hand-building HTTP error responses — the handler maps it by the exception's own `HttpStatus`.

**This only works because each `*Application.java` declares
`@SpringBootApplication(scanBasePackages = {"com.example.<module>", "com.example.commonlib"})`.**
`common-lib`'s `GlobalExceptionHandler`/`SwaggerConfig` are plain `@ControllerAdvice`/`@Configuration`
classes with no auto-configuration registration of their own — Spring Boot's default component scan
never leaves the `@SpringBootApplication` class's own package, so without `scanBasePackages` naming
the sibling `com.example.commonlib` package explicitly, these beans are never registered in *any* of
the three apps (full `@SpringBootTest`/runtime *or* `@WebMvcTest`). Registering common-lib as its own
`AutoConfiguration.imports`-based auto-configuration module was considered and rejected: `@WebMvcTest`
sets `@OverrideAutoConfiguration(enabled = false)` and only imports a small, fixed, framework-owned
allow-list of auto-configurations — a third-party auto-configuration is never reachable through that
slice no matter how it registers itself, so `scanBasePackages` (component scanning) is the only
mechanism that works uniformly across `@SpringBootTest`, `@WebMvcTest`, and production runtime. If you
add a new shared `@Component`/`@Configuration`/`@ControllerAdvice` to `common-lib`, it is reachable
automatically in all three apps already — no per-app wiring needed beyond what's there today.

Bean validation failures, malformed JSON, and missing params are already handled centrally; don't
re-catch them in controllers.

### Testing conventions (see `project2/src/test` for the reference shape)

Test classes are suffixed by the slice they exercise, and each carries a `Interview topic:` Javadoc
back-link when it documents a concept:

- `*IntegrationTest` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@LocalServerPort` +
  `TestRestTemplate`, full real Spring context.
- `*DataJpaTest` — `@DataJpaTest` + `TestEntityManager`, swaps in embedded H2 automatically.
- `*WebMvcTest` — `@WebMvcTest(SomeController.class)` + `MockMvc` + `@MockitoBean` for the service
  layer (Boot 4's replacement for the deprecated `@MockBean`) — don't use `@MockBean`.
- `*ServiceImplTest` (plain unit tests) — `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`,
  no Spring context at all.

Method names read as `methodUnderTest_condition_expectedOutcome`. AssertJ (`assertThat`,
`assertThatThrownBy`) throughout, not Hamcrest/JUnit asserts. `@SpringBootTest` and `@DataJpaTest`
both pick up `src/test/resources/application.properties`, which overrides the datasource to H2 and
disables Liquibase (`spring.liquibase.enabled=false`, `hibernate.ddl-auto=create-drop`) so tests
never touch the real Postgres/MySQL instance or need Docker running.

### Liquibase

Per-module, not shared: `<module>/src/main/resources/db/changelog/{db.changelog-master.xml,
changes/V<n>__create_<table>.sql}`, numbered sequentially per module (project3 additionally has a
`V0__create_keyspace.sql` first, since Cassandra needs the keyspace before any table).

### Spring Boot 4.0.3 renames (don't trust Boot-3-era tutorials/snippets for these)

| Boot 3 | Boot 4.0 |
|---|---|
| `org.springframework.boot.actuate.health.Health` / `.HealthIndicator` | `org.springframework.boot.health.contributor.Health` / `.HealthIndicator` |
| `org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer` | `org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer` |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` (needed for `@Timed`/`TimedAspect` to work) |
| `@MockBean` | `@MockitoBean` |
| (no starter) | `spring-boot-starter-liquibase` |

Boot 4.0 also **modularized `spring-boot-starter-test`** — `spring-boot-test`/`spring-boot-test-autoconfigure`
no longer carry per-technology test-slice annotations. `spring-boot-starter-test` alone gives you
JUnit/AssertJ/Mockito/`@SpringBootTest` and nothing web- or JPA-specific. Add the matching
`spring-boot-starter-<tech>-test` (test scope) for each slice you use, and update imports —
package names changed too, not just the jar:

| Boot 3 import | Needs this test-scope starter | Boot 4.0 import |
|---|---|---|
| `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` | `spring-boot-starter-webmvc-test` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| `org.springframework.boot.test.web.client.TestRestTemplate` | `spring-boot-starter-webmvc-test` (brings `spring-boot-resttestclient` transitively) **plus** `spring-boot-starter-restclient` (test scope) — see note below | `org.springframework.boot.resttestclient.TestRestTemplate` |
| `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` | `spring-boot-starter-data-jpa-test` | `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` |
| `org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager` | `spring-boot-starter-data-jpa-test` (brings `spring-boot-jpa-test` transitively) | `org.springframework.boot.jpa.test.autoconfigure.TestEntityManager` |

Also be aware, for `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`: Boot 4.0 no
longer auto-registers `TestRestTemplate` just because it's on the classpath and the webEnvironment
is a real port — the test class must carry `@AutoConfigureTestRestTemplate`
(`org.springframework.boot.resttestclient.autoconfigure`) explicitly, or `@Autowired private
TestRestTemplate` fails with `NoSuchBeanDefinitionException`. And even with that annotation present,
`TestRestTemplateTestAutoConfiguration` reflectively references `RestTemplateBuilder`
(`org.springframework.boot.restclient`, a *different* module from `spring-boot-resttestclient`) —
without `spring-boot-starter-restclient` (test scope) also on the classpath, the context fails with
`NoClassDefFoundError: org/springframework/boot/restclient/RestTemplateBuilder` instead. Both are
required together; see `project2/pom.xml` and `ProductApiIntegrationTest`.

Also be aware: Boot 4.0's default auto-configured `ObjectMapper` is now `tools.jackson.databind.ObjectMapper`
(Jackson 3, via `spring-boot-starter-jackson` → `spring-boot-jackson`, whose `JacksonAutoConfiguration`
only ever registers a `tools.jackson.databind.json.JsonMapper` bean), not
`com.fasterxml.jackson.databind.ObjectMapper` — a `@WebMvcTest`/`@SpringBootTest` that `@Autowired`s
the classic `com.fasterxml` type will fail the whole context with `NoSuchBeanDefinitionException`
even though the jar compiles fine (classic Jackson 2 classes can still be *on* the classpath
transitively, e.g. via `jackson-dataformat-xml`/`jackson-datatype-jsr310`, without Spring ever
registering a bean of that type). Boot 4.0 does ship a deprecated-on-arrival `spring-boot-jackson2`
bridge module for libraries that still need a classic Jackson 2 `ObjectMapper` bean during
migration, but it's scheduled for removal in a later 4.x release — don't reach for it in this repo;
write new code and tests against Jackson 3 (`tools.jackson.databind.ObjectMapper`/`JsonNode`)
instead, matching what Boot 4.0.3 actually auto-configures. `project2`'s test classes were migrated
to Jackson 3 on 2026-08-02, and the now-dead `jackson-dataformat-xml`/`jackson-datatype-jsr310`
main-scope dependencies (unused by any `src/main` code) were removed from `project2/pom.xml` in the
same pass — `project1`/`project3`/`common-lib` still carry the same two dependencies and haven't
been audited for the same dead weight.

### Publishing constraint

This repo is a public portfolio. Never write a real company/employer name or a work email domain
into any file under this tree — use `example.com` / `localhost` placeholders. Don't touch `cv/`
(outside this reactor, but in the same repo) — out of scope regardless of task.
