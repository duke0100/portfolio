---
name: project-commonlib-scanbasepackages
description: common-lib beans (GlobalExceptionHandler, SwaggerConfig) require scanBasePackages on every *Application class, not auto-configuration
metadata:
  type: project
---

Fixed 2026-08-02: `common-lib`'s `GlobalExceptionHandler` (`@ControllerAdvice`) and `SwaggerConfig`
(`@Configuration`) were never registered as beans in any of `project1`/`project2`/`project3` — Spring
Boot's default component scan starts at the `@SpringBootApplication` class's own package and never
reaches the sibling `com.example.commonlib` package. Fix applied to all three `*Application.java`:
`@SpringBootApplication(scanBasePackages = {"com.example.<module>", "com.example.commonlib"})`.

**Why this fix and not `common-lib`-as-auto-configuration**: `@WebMvcTest` sets
`@OverrideAutoConfiguration(enabled = false)` and only imports a small, fixed, framework-owned
allow-list of auto-configurations (verified by reading the `@WebMvcTest` annotation source in
`spring-boot-webmvc-test-4.0.3-sources.jar`). A custom `AutoConfiguration.imports`-based module from
a shared jar is **never** reachable through that slice, no matter how it registers itself — only
component scanning (filtered by `WebMvcTypeExcludeFilter` to `@Controller`/`@ControllerAdvice`/etc.)
works, and that only sees `com.example.commonlib` if `scanBasePackages` names it explicitly.
`@SpringBootTest` would have worked with either approach, but `@WebMvcTest` would not have with
auto-configuration — so component scan is the only mechanism that works uniformly across all test
slices and production runtime.

**How to apply:** Any new shared `@Component`/`@Configuration`/`@ControllerAdvice` added to
`common-lib` is already reachable in all three apps via this `scanBasePackages` — no new per-app
wiring needed. If a 4th module is ever added to this reactor, its `*Application.java` needs the same
`scanBasePackages` pattern. Full detail in
`back-end/spring-boot/practice/CLAUDE.md` under "common-lib contract".