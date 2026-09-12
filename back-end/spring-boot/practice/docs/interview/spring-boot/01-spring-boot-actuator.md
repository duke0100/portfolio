# Spring Boot Actuator and which endpoints to expose in production

Applied in [project2](../../../project2) (Web + JPA + PostgreSQL, ~1M product rows).
Every snippet below is real code — the link above each block opens the file it came from, and each
of those files carries an `Interview topic:` back-link to the section here.

| Section | Code in project2 |
|---|---|
| [Answer](#answer) | [`pom.xml`](../../../project2/pom.xml) |
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties), [`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties) |
| [Liveness and readiness](#liveness-and-readiness) | [`application.properties`](../../../project2/src/main/resources/application.properties) |
| [Custom health indicator](#custom-health-indicator) | [`ProductCatalogHealthIndicator.java`](../../../project2/src/main/java/com/example/project2/actuator/ProductCatalogHealthIndicator.java) |
| [Custom metrics](#custom-metrics) | [`ProductController.java`](../../../project2/src/main/java/com/example/project2/controller/ProductController.java), [`MetricsConfig.java`](../../../project2/src/main/java/com/example/project2/actuator/MetricsConfig.java), [`ProductServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductServiceImpl.java) |
| [Custom info](#custom-info) | [`CatalogInfoContributor.java`](../../../project2/src/main/java/com/example/project2/actuator/CatalogInfoContributor.java) |

## Answer

Actuator is a starter that adds ready-made operational HTTP endpoints (health, metrics, info, log
levels, thread dumps) — one dependency, no code required.

In production, expose just four: `health`, `info`, `metrics`, `prometheus` — on a separate
management port that isn't reachable from the internet, with `health.show-details=when-authorized`.
Anything that dumps internal state (`env`, `configprops`, `beans`, `heapdump`, `threaddump`,
`loggers`, `mappings`) should stay off, or be locked behind admin auth.

[`project2/pom.xml`](../../../project2/pom.xml):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<!-- Needed by TimedAspect so @Timed is honoured (Boot 4 renamed starter-aop to starter-aspectj) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aspectj</artifactId>
</dependency>
<!-- Enables /actuator/prometheus -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <scope>runtime</scope>
</dependency>
```

By default, only `health` is exposed over HTTP — everything else exists but stays unreachable
until it's explicitly listed.

| Endpoint | Output | Production? |
|---|---|---|
| `health` | Up/down + per-component detail (db, disk, custom) | **Yes** — LB / orchestrator probe |
| `info` | Build version, git commit, contributed blocks | **Yes** |
| `metrics` | One Micrometer meter per call | **Yes** |
| `prometheus` | All meters in scrape format | **Yes** |
| `loggers` | Read **and write** log levels at runtime | Admin-only (write endpoint) |
| `env` | Every property, resolved secrets included | No |
| `configprops` | Every `@ConfigurationProperties` bean | No |
| `beans` | Full bean graph | No |
| `mappings` | Every URL served | No |
| `threaddump` | Full stack dump | No |
| `heapdump` | Heap file, secrets and user data included | Never |
| `shutdown` | Stops the app over HTTP | Never (off by default) |

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
management.server.port=9082
management.endpoints.web.base-path=/actuator
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoints.web.exposure.exclude=env,beans,configprops,heapdump,threaddump,loggers,mappings

management.endpoint.health.show-details=when-authorized
management.endpoint.health.show-components=when-authorized
management.endpoint.health.roles=ACTUATOR_ADMIN
```

| Choice | vs the alternative |
|---|---|
| `management.server.port=9082` | vs sharing 8082: a separate port means a firewall/`NetworkPolicy` can block it, so an exposure mistake here still isn't internet-facing |
| `include=<list>` allow-list | vs `include=*`: `*` also exposes `env` and `heapdump`; the `exclude` line here is just belt-and-braces documentation of intent |
| `show-details=when-authorized` | vs `always`: `always` leaks the DB URL, disk paths, and component names to any caller. It only means something with Spring Security in place — project2 has none, so it's effectively "never" here |

[`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties)
— the same settings, deliberately relaxed so the endpoints are usable locally:

```properties
management.server.port=8082
management.endpoints.web.exposure.include=*
management.endpoints.web.exposure.exclude=
management.endpoint.health.show-details=always
management.endpoint.health.show-components=always
```

### Liveness and readiness

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
management.endpoint.health.probes.enabled=true
management.health.livenessstate.enabled=true
management.health.readinessstate.enabled=true
management.endpoint.health.group.liveness.include=livenessState,ping
management.endpoint.health.group.readiness.include=readinessState,db,productCatalog
management.endpoint.health.group.readiness.show-details=always
```

| Probe | URL | On failure | Include |
|---|---|---|---|
| liveness | `/actuator/health/liveness` | container **restarted** | almost nothing — never the DB (a DB blip would otherwise trigger a cluster-wide restart storm) |
| readiness | `/actuator/health/readiness` | instance **removed from pool** | dependencies the app genuinely can't work without |

## Custom health indicator

[`ProductCatalogHealthIndicator.java`](../../../project2/src/main/java/com/example/project2/actuator/ProductCatalogHealthIndicator.java)
— the bean name minus its `HealthIndicator` suffix becomes the component key, so this one shows up
as `productCatalog` (the same name used in the readiness group above):

```java
@Component
@RequiredArgsConstructor
public class ProductCatalogHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        long startNanos = System.nanoTime();
        try {
            boolean hasProducts = productRepository.findAll(PageRequest.of(0, 1)).hasContent();
            long tookMillis = (System.nanoTime() - startNanos) / 1_000_000;
            return Health.up()
                    .withDetail("probeMillis", tookMillis)
                    .withDetail("slow", tookMillis >= SLOW_PROBE_MILLIS)
                    .withDetail("catalogEmpty", !hasProducts)
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

- Uses `findAll(PageRequest.of(0, 1))` instead of `count()`, because PostgreSQL has no cached row
  count — `count(*)` on ~1M rows means a full table scan, on a probe that's called every few seconds.
- A slow response reports `UP` with a `slow` detail flag, not `DOWN` — only a thrown exception
  counts as `DOWN`. Otherwise, one bad query plan would drain every instance from the pool at once.

`GET /actuator/health` →

```json
{"status":"UP","components":{"db":{"status":"UP"},
 "productCatalog":{"status":"UP","details":{"probeMillis":7,"slow":false,"catalogEmpty":false}}}}
```

Overall status is whichever component is worst: `DOWN` > `OUT_OF_SERVICE` > `UP` > `UNKNOWN`.

## Custom metrics

[`ProductController.findAll`](../../../project2/src/main/java/com/example/project2/controller/ProductController.java)
— `GET /api/v1/products?page=0&size=20`:

```java
@Timed(value = "product.api",
        extraTags = {"operation", "findAll"},
        description = "Paged product listing",
        percentiles = {0.5, 0.95, 0.99})
@GetMapping
public ApiResponse<Page<ProductResponse>> findAll(
        @PageableDefault(size = 20) Pageable pageable) {
    return ApiResponse.ok(productService.findAll(pageable));
}
```

`@Timed` does nothing without a `TimedAspect` bean —
[`MetricsConfig`](../../../project2/src/main/java/com/example/project2/actuator/MetricsConfig.java)
provides one, and also needs `spring-boot-starter-aspectj` on the classpath:

```java
@Bean
public TimedAspect timedAspect(MeterRegistry meterRegistry) {
    return new TimedAspect(meterRegistry);
}

@Bean
public MeterRegistryCustomizer<MeterRegistry> commonTags(
        @Value("${spring.application.name}") String applicationName) {
    return registry -> registry.config().commonTags("application", applicationName);
}
```

[`ProductServiceImpl.findById`](../../../project2/src/main/java/com/example/project2/service/impl/ProductServiceImpl.java)
— `GET /api/v1/products/{id}`, counted `hit` on success and `miss` on the 404 path:

```java
private void countLookup(String result) {
    meterRegistry.counter("product.lookup", "result", result).increment();
}
```

Each distinct tag value creates its own time series. `result` only has two values, which is fine;
tagging by product id would create a million of them. Never tag with a user id, request id, or raw
URL path for the same reason.

`GET /actuator/prometheus` →

```text
product_lookup_total{application="project2-postgresql",result="hit"} 42.0
product_lookup_total{application="project2-postgresql",result="miss"} 3.0
product_api_seconds{application="project2-postgresql",operation="findAll",quantile="0.95"} 0.031
```

## Custom info

[`CatalogInfoContributor`](../../../project2/src/main/java/com/example/project2/actuator/CatalogInfoContributor.java):

```java
@Component
public class CatalogInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("module", "project2");
        catalog.put("database", "postgresql");
        catalog.put("purpose", "large-dataset query and indexing practice");
        builder.withDetail("catalog", catalog);
    }
}
```

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
management.info.build.enabled=true
management.info.java.enabled=true
management.info.env.enabled=true
info.app.owner=backend-practice
```

`build.enabled` reads `META-INF/build-info.properties`, which the `build-info` goal in
[`pom.xml`](../../../project2/pom.xml) generates; adding `git-commit-id-maven-plugin` would also
include the commit hash.

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals><goal>build-info</goal></goals>
        </execution>
    </executions>
</plugin>
```

`GET /actuator/info` →

```json
{"build":{"artifact":"project2","version":"1.0.0-SNAPSHOT"},
 "app":{"owner":"backend-practice"},
 "catalog":{"module":"project2","database":"postgresql","purpose":"..."}}
```

## Securing it

| Approach | Code | Good for |
|---|---|---|
| Separate port + network rule | `management.server.port=9082` + firewall | Kubernetes; the default choice |
| Spring Security rule | `requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole("ACTUATOR_ADMIN")` | Admin access over the same port |
| Obscure base path | `management.endpoints.web.base-path=/internal/ops` | Extra layer only, weak alone |

`EndpointRequest.toAnyEndpoint()` always reflects the current list of endpoints, so it keeps
working even if the base path or exposure list changes later.

## Spring Boot 4 notes

This repo builds on `spring-boot-starter-parent` **4.0.3**, which split Actuator into several
modules. Property names are unchanged, but some package names (and one artifact id) are — see the
imports in
[`ProductCatalogHealthIndicator.java`](../../../project2/src/main/java/com/example/project2/actuator/ProductCatalogHealthIndicator.java)
and [`MetricsConfig.java`](../../../project2/src/main/java/com/example/project2/actuator/MetricsConfig.java).

| Boot 3 | Boot 4.0 |
|---|---|
| `org.springframework.boot.actuate.health.Health` / `.HealthIndicator` | `org.springframework.boot.health.contributor.Health` / `.HealthIndicator` |
| `org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer` | `org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer` |
| `org.springframework.boot.actuate.info.InfoContributor` | unchanged |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| `exposure.include=*` | Also ships `env` and `heapdump` |
| `show-details=always` with no auth | Leaks DB URLs, disk paths, dependency topology to anyone |
| Database in the liveness probe | A DB blip triggers a cluster-wide restart storm |
| `count(*)` in a health indicator | A slow probe on a big table, called constantly |
| `@Timed` with no `TimedAspect` bean | Silently does nothing |
| High-cardinality tags | Time-series explosion, can overload the metrics backend |
| Actuator on the public port | One config slip becomes an internet-facing data leak |
| Health indicator with no timeout | A hung probe fills the thread pool, and health stops answering at all |
| Deep downstream checks in `/health` | Each probe multiplies load onto every dependency it checks |

## Follow-up questions

**Overall health status?** Whichever component is worst wins: `DOWN` > `OUT_OF_SERVICE` > `UP` >
`UNKNOWN`. A custom status can be mapped to an HTTP code via
`management.endpoint.health.status.http-mapping`.

**`HealthIndicator` vs `HealthContributor`?** A `HealthIndicator` returns one `Health` result; a
`CompositeHealthContributor` groups several named ones under one key (for example, one per
downstream dependency).

**Adding your own endpoint?** Put `@Endpoint(id = "cache")` on a bean with `@ReadOperation` /
`@WriteOperation` / `@DeleteOperation` methods; `@WebEndpoint` exposes it over HTTP only,
`@JmxEndpoint` over JMX only. Then add its id to `include`.

**Getting metrics into Prometheus?** Add `micrometer-registry-prometheus` to expose
`/actuator/prometheus` — Prometheus scrapes it on an interval, the app never pushes anything.
Datadog/StatsD work the other way: they push, and need an interval plus credentials configured.

**Performance cost?** Endpoints cost nothing unless someone calls them; recording a metric is a
few cheap operations per request. The real costs are percentile histograms (memory per meter) and
health indicators that hit the database.

**Health during graceful shutdown?** With `server.shutdown=graceful` and
`management.endpoint.health.group.readiness.include=readinessState`, readiness flips to
`REFUSING_TRAFFIC` as soon as shutdown starts, so the load balancer can drain traffic before
in-flight requests finish.

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am spring-boot:run -Dspring-boot.run.profiles=dev

curl localhost:8082/api/v1/products?page=0&size=20   # generates product.api + product.lookup
curl localhost:8082/actuator/health                  # productCatalog visible under dev profile
curl localhost:8082/actuator/health/readiness
curl localhost:8082/actuator/info
curl "localhost:8082/actuator/metrics/product.api?tag=operation:findAll"
curl localhost:8082/actuator/prometheus | grep product_lookup
```

Without the `dev` profile: port **9082**, status only.
