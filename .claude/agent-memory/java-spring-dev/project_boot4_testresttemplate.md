---
name: project-boot4-testresttemplate
description: Boot 4.0.3 TestRestTemplate needs @AutoConfigureTestRestTemplate + spring-boot-starter-restclient test dep, beyond spring-boot-starter-webmvc-test
metadata:
  type: project
---

Discovered/fixed 2026-08-02 in `project2`'s `ProductApiIntegrationTest`
(`@SpringBootTest(webEnvironment = RANDOM_PORT)`): Boot 4.0.3 no longer auto-registers
`TestRestTemplate` (`org.springframework.boot.resttestclient.TestRestTemplate`) just because
`spring-boot-starter-webmvc-test` is on the test classpath and the webEnvironment is a real port.
Two things are required together, both easy to miss since neither fails loudly with a clear message
pointing at the real cause:

1. `@AutoConfigureTestRestTemplate` (`org.springframework.boot.resttestclient.autoconfigure`)
   annotation on the test class itself — without it, `@Autowired TestRestTemplate` fails with
   `NoSuchBeanDefinitionException`.
2. `spring-boot-starter-restclient` as a **test-scope** Maven dependency — even with the annotation
   present, `TestRestTemplateTestAutoConfiguration` reflectively touches `RestTemplateBuilder`
   (`org.springframework.boot.restclient`, a *different* Boot module from `spring-boot-resttestclient`
   that `spring-boot-starter-webmvc-test` does NOT pull in transitively), and its absence throws
   `NoClassDefFoundError: org/springframework/boot/restclient/RestTemplateBuilder` when Spring tries
   to introspect the auto-configuration class via reflection.

**Why:** Boot 4.0 split `spring-boot-starter-test` into many per-technology starters
(see `back-end/spring-boot/practice/CLAUDE.md`'s modularization table) — `TestRestTemplate` support
and `RestTemplateBuilder` ended up in two separate modules that don't depend on each other.

**How to apply:** Whenever adding a new `@SpringBootTest(webEnvironment = RANDOM_PORT)` test with
`TestRestTemplate` in any Boot 4.0.3 module in this repo, add both the annotation and the test-scope
`spring-boot-starter-restclient` dependency up front rather than discovering the two failures one at
a time. See `project2/pom.xml` and `project2/src/test/java/com/example/project2/ProductApiIntegrationTest.java`
for the reference shape.
