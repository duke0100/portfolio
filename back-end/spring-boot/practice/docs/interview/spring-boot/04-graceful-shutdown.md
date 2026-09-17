# How do you handle graceful shutdown in Spring Boot?

Applied in [project2](../../../project2) (Web + JPA + PostgreSQL, Actuator with readiness probes on
a separate management port).
Every snippet below is real code — the link above each block opens the file it came from, and each
of those files carries an `Interview topic:` back-link to the section here.

| Section | Code in project2 |
|---|---|
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties), [`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties), [`GracefulShutdownProperties.java`](../../../project2/src/main/java/com/example/project2/config/GracefulShutdownProperties.java), [`GracefulShutdownConfig.java`](../../../project2/src/main/java/com/example/project2/config/GracefulShutdownConfig.java) |
| [Draining before the server stops](#draining-before-the-server-stops) | [`ShutdownDrainListener.java`](../../../project2/src/main/java/com/example/project2/lifecycle/ShutdownDrainListener.java), [`ShutdownState.java`](../../../project2/src/main/java/com/example/project2/lifecycle/ShutdownState.java) |
| [Non-web work](#non-web-work) | [`BackgroundWorkGate.java`](../../../project2/src/main/java/com/example/project2/lifecycle/BackgroundWorkGate.java), [`IdempotencyCleanupJob.java`](../../../project2/src/main/java/com/example/project2/service/impl/IdempotencyCleanupJob.java), [`CatalogAsyncConfig.java`](../../../project2/src/main/java/com/example/project2/config/CatalogAsyncConfig.java), [`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java) |
| [Watching the shutdown](#watching-the-shutdown) | [`LifecycleController.java`](../../../project2/src/main/java/com/example/project2/controller/LifecycleController.java), [`ShutdownProbeService.java`](../../../project2/src/main/java/com/example/project2/service/ShutdownProbeService.java) |
| [Proving the drain](#proving-the-drain) | [`ShutdownProbeServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ShutdownProbeServiceImpl.java), [`DrainProbeResponse.java`](../../../project2/src/main/java/com/example/project2/dto/response/DrainProbeResponse.java) |
| [Under load](#under-load) | [`GracefulShutdownConcurrencyTest.java`](../../../project2/src/test/java/com/example/project2/lifecycle/GracefulShutdownConcurrencyTest.java) |

## Answer

`server.shutdown=graceful` stops the server accepting new connections on SIGTERM and lets the
requests already running finish. Boot's default, `immediate`, cuts them off mid-response.
`spring.lifecycle.timeout-per-shutdown-phase` (default 30s) is the budget per shutdown phase;
anything still running when it expires is interrupted.

That covers HTTP only. Thread pools, scheduled jobs and message listeners each need their own
shutdown setting.

The half most people miss is the load balancer, which keeps routing to you for a second or two
after SIGTERM. Flip readiness to out-of-service, keep serving briefly, then close the port.

| Step | What happens | Where you hook in |
|---|---|---|
| SIGTERM | The shutdown hook Boot registered calls `context.close()` | — |
| `ContextClosedEvent` | Published before anything is stopped, server still accepting | `ApplicationListener<ContextClosedEvent>` |
| Lifecycle `stop()`, highest phase first | Connector stops accepting, waits for in-flight requests | `SmartLifecycle` with a phase |
| `destroyBeans()` | `@PreDestroy`, `DisposableBean`, Hikari closes the pool | `@PreDestroy` / `DisposableBean` |
| JVM exits | Daemon threads are killed wherever they are | — |
| SIGKILL | Nothing runs at all | — |

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
# The connector stops accepting new connections on SIGTERM and lets the requests already being
# handled finish. The Boot default is "immediate", which cuts them mid-response.
server.shutdown=graceful

# Budget per shutdown phase, the web server's drain included. Boot's default is 30s; 25s leaves
# room under a Kubernetes terminationGracePeriodSeconds of 30 before SIGKILL lands.
spring.lifecycle.timeout-per-shutdown-phase=25s

# Scheduled sweeps get the same treatment as the @Async pool above: finish the tick in progress
# instead of dying inside a transaction.
spring.task.scheduling.shutdown.await-termination=true
spring.task.scheduling.shutdown.await-termination-period=15s

# How long we keep serving after SIGTERM before the web server stops accepting, so the load
# balancer has time to notice readiness went OUT_OF_SERVICE and stop routing to us.
# Must be longer than the LB's own health-check interval, or it buys nothing.
catalog.shutdown.deregistration-delay=PT5S
```

| Choice | vs the alternative |
|---|---|
| `server.shutdown=graceful` | vs `immediate` (the default): in-flight responses are cut, so a deploy shows up as 502s |
| `timeout-per-shutdown-phase=25s` | vs the 30s default: it must stay *under* the pod grace period, or SIGKILL arrives while we are still draining |
| `deregistration-delay=PT5S` | vs `PT0S`: zero means the port closes while the load balancer still lists us, which is the connection-refused race |
| `task.scheduling.shutdown.await-termination=true` | vs the default `false`: a sweep in progress is abandoned mid-transaction |

[`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties)
— the same knobs set the unsafe way on purpose:

```properties
server.shutdown=immediate
spring.lifecycle.timeout-per-shutdown-phase=5s
catalog.shutdown.deregistration-delay=PT0S
```

[`GracefulShutdownProperties.java`](../../../project2/src/main/java/com/example/project2/config/GracefulShutdownProperties.java)
— bound by [`GracefulShutdownConfig`](../../../project2/src/main/java/com/example/project2/config/GracefulShutdownConfig.java):

```java
@ConfigurationProperties(prefix = "catalog.shutdown")
public class GracefulShutdownProperties {

    /** How long we keep serving after SIGTERM so the load balancer notices we are out of service. */
    private Duration deregistrationDelay = Duration.ofSeconds(5);

    /** How often the drain logs the number of requests still running. */
    private Duration drainLogInterval = Duration.ofSeconds(1);

    /** Ceiling on the drain probe's hold time, so the probe can never outlast the grace period. */
    private Duration maxProbeHold = Duration.ofSeconds(30);
}
```

- Defaults live in the class, so a test that shadows `application.properties` still shuts down
  properly.

## Draining before the server stops

[`ShutdownDrainListener.java`](../../../project2/src/main/java/com/example/project2/lifecycle/ShutdownDrainListener.java)
— runs while the server is still accepting, because Spring publishes `ContextClosedEvent` before
it stops any lifecycle bean:

```java
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class ShutdownDrainListener implements ApplicationListener<ContextClosedEvent> {

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        // The management server runs in a child context, and a child event reaches the parent's
        // listeners too. Without this check the deregistration pause would run twice per shutdown.
        if (!applicationContext.equals(event.getApplicationContext())) {
            return;
        }
        shutdownState.markShutdownStarted();

        // Readiness goes OUT_OF_SERVICE, so /actuator/health/readiness answers 503 on the next poll
        // while /actuator/health/liveness stays UP - "stop sending traffic", not "restart me".
        AvailabilityChangeEvent.publish(event.getApplicationContext(), ReadinessState.REFUSING_TRAFFIC);

        Duration delay = properties.getDeregistrationDelay();
        if (delay.isZero() || delay.isNegative()) {
            log.info("Shutdown: refusing traffic, no deregistration delay configured");
            return;
        }

        log.info("Shutdown: refusing traffic, still serving for {} so the load balancer can drop us", delay);
        waitForDeregistration(delay);
        log.info("Shutdown: deregistration window over, {} request(s) still in flight",
                shutdownState.activeHttpRequests());
    }
}
```

- Blocking here is the point. The close waits, and requests keep being served meanwhile.
- The context check is not optional. `management.server.port=9082` puts Actuator in a child
  context, whose `ContextClosedEvent` also reaches this listener, and the pause would be paid twice.

[`ShutdownState.java`](../../../project2/src/main/java/com/example/project2/lifecycle/ShutdownState.java)
— one flag plus the in-flight count Micrometer already tracks:

```java
    /** Written once by the shutdown listener, read by request threads, hence volatile. */
    private volatile Instant shutdownStartedAt;

    public long activeHttpRequests() {
        return meterRegistry.find("http.server.requests.active").longTaskTimers().stream()
                .mapToLong(LongTaskTimer::activeTasks)
                .sum();
    }
```

- Per-instance state on purpose: each instance gets its own SIGTERM and flips its own flag.

## Non-web work

Graceful shutdown covers HTTP only. Three other kinds of work need their own answer.

[`BackgroundWorkGate.java`](../../../project2/src/main/java/com/example/project2/lifecycle/BackgroundWorkGate.java)
— a `SmartLifecycle` whose phase decides *when* in the sequence it closes:

```java
@Component
public class BackgroundWorkGate implements SmartLifecycle {

    /** Low enough to stop after the web server, high enough to stop before anything is destroyed. */
    private static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 2048;

    /** Read by scheduler threads, written by the lifecycle thread, hence volatile. */
    private volatile boolean accepting;

    @Override
    public void stop() {
        accepting = false;
        log.info("Shutdown: background jobs closed for new work");
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
```

- Spring stops lifecycle beans from the highest phase down and the web server sits just under
  `DEFAULT_PHASE`, so this closes after the last HTTP request and before the first `@PreDestroy`.

[`IdempotencyCleanupJob.java`](../../../project2/src/main/java/com/example/project2/service/impl/IdempotencyCleanupJob.java)
— the scheduled sweep asks the gate first:

```java
    @Scheduled(fixedDelayString = "${idempotency.cleanup-interval:PT15M}")
    public void purgeExpiredKeys() {
        if (!backgroundWorkGate.isAccepting()) {
            log.debug("Skipping idempotency sweep: this instance is shutting down");
            return;
        }
        int deleted = store.purgeExpired(Instant.now(), properties.getCleanupBatchSize());
```

[`CatalogAsyncConfig.java`](../../../project2/src/main/java/com/example/project2/config/CatalogAsyncConfig.java)
— the `@Async` pool drains itself:

```java
        // Finish queued stages on SIGTERM instead of dropping them, but stop waiting after 10s so
        // we never blow the pod grace period.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
```

[`CatalogTaskExecutor.java`](../../../project2/src/main/java/com/example/project2/concurrent/CatalogTaskExecutor.java)
— a raw `ExecutorService` has to be drained by hand, in `DisposableBean.destroy()`:

```java
    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
            log.warn("Catalog tasks still running after {}s, interrupting the rest", SHUTDOWN_WAIT_SECONDS);
            executor.shutdownNow();
        }
    }
```

- `shutdown()` then `awaitTermination` then `shutdownNow()` is the whole pattern: stop taking work,
  wait a bounded time, interrupt the rest.

## Watching the shutdown

[`LifecycleController.java`](../../../project2/src/main/java/com/example/project2/controller/LifecycleController.java):

```java
@RestController
@RequestMapping("/api/v1/lifecycle")
@RequiredArgsConstructor
public class LifecycleController {

    /** What this instance is doing: serving, draining, or refusing traffic. */
    @GetMapping("/state")
    public ApiResponse<LifecycleStateResponse> state() {
        return ApiResponse.ok(shutdownProbeService.currentState());
    }
```

`GET /api/v1/lifecycle/state` →

```json
{
  "success": true,
  "data": {
    "shutdownMode": "graceful",
    "deregistrationDelay": "PT5S",
    "shuttingDown": false,
    "readinessState": "ACCEPTING_TRAFFIC",
    "activeHttpRequests": 1,
    "backgroundJobsAccepting": true
  }
}
```

- `readinessState` is the value `/actuator/health/readiness` reports, readable from the app port
  when the management port is not reachable.

## Proving the drain

[`ShutdownProbeServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ShutdownProbeServiceImpl.java)
— one request held open, so there is something in flight when SIGTERM lands:

```java
    @Override
    public DrainProbeResponse hold(long holdMillis) {
        long maxHoldMillis = properties.getMaxProbeHold().toMillis();
        if (holdMillis <= 0 || holdMillis > maxHoldMillis) {
            throw BusinessException.badRequest(
                    "holdMillis must be between 1 and " + maxHoldMillis);
        }

        boolean startedDuringShutdown = shutdownState.isShuttingDown();
        LongTaskTimer.Sample sample = LongTaskTimer.builder(ACTIVE_PROBES_METER)
                .description("Drain probe requests currently in flight")
                .register(meterRegistry)
                .start();
        long startNanos = System.nanoTime();
        try {
            TimeUnit.MILLISECONDS.sleep(holdMillis);
        } catch (InterruptedException e) {
            // Restore the flag; the caller sees a 503 rather than a truncated 200.
            Thread.currentThread().interrupt();
            throw new BusinessException("Drain probe interrupted by shutdown",
                    HttpStatus.SERVICE_UNAVAILABLE, "SHUTDOWN_INTERRUPTED");
        } finally {
            sample.stop();
        }
```

- The cap (`catalog.shutdown.max-probe-hold`) keeps the probe from outlasting the grace period.
- The `LongTaskTimer` is what the test waits on, and the gauge to watch during a real deploy.

`GET /api/v1/lifecycle/drain-probe?holdMillis=5000` →

```json
{
  "success": true,
  "data": {
    "requestedHoldMillis": 5000,
    "actualHoldMillis": 5001,
    "startedDuringShutdown": false,
    "finishedDuringShutdown": true
  }
}
```

## Comparison

| Aspect | `server.shutdown=graceful` | `server.shutdown=immediate` |
|---|---|---|
| What it does | Stops accepting, finishes in-flight requests | Closes the connector at once |
| When it applies | Any `context.close()`, so SIGTERM and Ctrl+C | Same trigger, no waiting |
| Performance at 1M rows | A slow report request delays the whole shutdown by its own duration | Shutdown is instant, that report is lost |
| Under concurrent callers | Every request already accepted still gets its response | Each gets a dropped connection |
| With N instances | One instance drains while the others serve; the rollout is invisible | Every rollout sheds a burst of errors |
| Failure mode | Requests over the phase timeout are interrupted anyway | Requests are always cut |
| Use when | Anything deployed behind a load balancer | Local edit-run loops only |

Rule of thumb: graceful in every deployed profile, with a pause in front of it. The drain saves the
requests you have, the pause saves the ones still coming.

## Under load

| Hazard | What happens with concurrent callers / N instances | What this code does about it |
|---|---|---|
| Eight checkouts mid-flight when the pod is replaced | `immediate` cuts all eight connections; the client sees a 502 and may retry a half-applied write | `server.shutdown=graceful` holds the close until every one of them has answered |
| The load balancer has not noticed yet | Requests keep arriving for a second or two after the port closes — connection refused, and no retry helps | Readiness flips to `REFUSING_TRAFFIC` and we keep serving for `catalog.shutdown.deregistration-delay` |
| The scheduled sweep fires mid-shutdown | With N instances every one of them sweeps anyway; a tick during shutdown opens a transaction on a closing pool | `BackgroundWorkGate` closes at its lifecycle phase, before any bean is destroyed |
| A request longer than the phase timeout | It is interrupted at 25s and the caller still gets a broken response | The probe hold is capped; real long work belongs in a job, not a request |

[`GracefulShutdownConcurrencyTest.java`](../../../project2/src/test/java/com/example/project2/lifecycle/GracefulShutdownConcurrencyTest.java)
— eight requests are in flight when the context closes; all eight must still get a 200:

```java
            startGun.countDown();
            awaitActiveProbes(meterRegistry);

            // Both numbers are read before the close, so the shutdown demonstrably starts while
            // all eight requests are inside the handler.
            assertThat(activeProbes(meterRegistry)).isEqualTo(CALLERS);
            assertThat(context.getBean(ShutdownState.class).activeHttpRequests())
                    .isGreaterThanOrEqualTo(CALLERS);

            long closeStartNanos = System.nanoTime();
            context.close(); // this is what SIGTERM does
            long closeMillis = (System.nanoTime() - closeStartNanos) / 1_000_000;

            // The close blocked on the in-flight requests instead of cutting them off.
            assertThat(closeMillis).isGreaterThan(HOLD_MILLIS / 2);

            for (Future<ResponseEntity<String>> result : inFlight) {
                ResponseEntity<String> response = result.get(AWAIT_SECONDS, TimeUnit.SECONDS);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                ...
            }

            assertThat(context.isActive()).isFalse();
            // Nothing is listening any more, so a late caller is refused at the socket instead of
            // being served by a half-closed context.
            assertThatThrownBy(() -> restTemplate.getForEntity(url(port, "/api/v1/lifecycle/state"), String.class))
                    .isInstanceOf(ResourceAccessException.class);
```

```bash
mvn -pl project2 -am test -Dtest=GracefulShutdownConcurrencyTest
```

It starts its own context, because it closes that context on purpose. The JVM shutdown hook and the
SIGKILL after it are not covered - those need a real process.

Watch during a real deploy. An active count that never reaches zero means a handler is stuck and
the phase timeout is about to interrupt it:

```bash
curl localhost:9082/actuator/metrics/http.server.requests.active
curl localhost:9082/actuator/metrics/hikaricp.connections.pending
curl -i localhost:9082/actuator/health/readiness   # 503 OUT_OF_SERVICE once SIGTERM lands
```

## Multiple instances and Kubernetes

A rolling deploy is clean only if the timers line up: the pause outlasts the health-check interval,
and the grace period outlasts the pause plus the drain.

<!-- not in this repo -->
```yaml
spec:
  terminationGracePeriodSeconds: 45   # > deregistration delay + timeout-per-shutdown-phase
  containers:
    - name: project2
      readinessProbe:
        httpGet: { path: /actuator/health/readiness, port: 9082 }
        periodSeconds: 2                # shorter than catalog.shutdown.deregistration-delay=PT5S
      livenessProbe:
        httpGet: { path: /actuator/health/liveness, port: 9082 }
```

| Knob | Value here | Why |
|---|---|---|
| `terminationGracePeriodSeconds` | 45 | SIGKILL lands after it; must cover 5s pause + 25s phase timeout |
| `catalog.shutdown.deregistration-delay` | `PT5S` | Longer than the 2s probe period, so the endpoint list is updated before the port closes |
| `spring.lifecycle.timeout-per-shutdown-phase` | 25s | Leaves headroom under the grace period |
| `preStop` hook | not needed | The in-app pause does the same job; use `sleep 5` instead if you cannot change the app |

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| `server.shutdown=graceful` and nothing else | Only HTTP drains. `@Async` pools, schedulers and listeners still die mid-task |
| No deregistration pause | The drain finishes cleanly and callers still get connection refused, because the LB had not removed you yet |
| `docker stop` / `kill -9` | `docker stop` sends SIGTERM but SIGKILLs after 10s by default, which is shorter than most grace periods |
| Phase timeout longer than the pod grace period | SIGKILL arrives mid-drain, so the graceful config buys nothing |
| Daemon threads doing real work | They are killed wherever they are, with no `@PreDestroy` and no rollback |
| Assuming a listener only hears its own context | The management server is a child context; its `ContextClosedEvent` reaches the parent's listeners too |
| Long-running requests | A 60s export outlives the phase timeout and is interrupted anyway — move it to a job |

## Follow-up questions

**What actually triggers the shutdown?** `SpringApplication` registers a JVM shutdown hook that
calls `context.close()`. SIGTERM runs it; `kill -9` does not run anything at all.

**Order of `@PreDestroy`, `DisposableBean` and `SmartLifecycle.stop()`?** All `stop()` calls run
first, highest phase down. Then `destroyBeans()` runs `@PreDestroy` and `DisposableBean` per bean,
in reverse dependency order.

**When does the connection pool close?** Hikari is a normal bean, so it closes in `destroyBeans()`
after every lifecycle `stop()`. Work that needs a connection must finish in a lifecycle phase.

**What happens when `timeout-per-shutdown-phase` expires?** The remaining lifecycle beans in that
phase are interrupted and the close moves on. In-flight requests get a broken response.

**Does graceful shutdown wait for `@Async` tasks?** No. They are covered by
`spring.task.execution.shutdown.await-termination` or the executor's own
`setWaitForTasksToCompleteOnShutdown`.

**Kafka or RabbitMQ listeners?** Their containers are `SmartLifecycle` beans: they finish the
current message, stop polling, then let the context close.

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am spring-boot:run

curl "localhost:8088/api/v1/lifecycle/state"                       # readinessState: ACCEPTING_TRAFFIC
curl "localhost:8088/api/v1/lifecycle/drain-probe?holdMillis=8000" &
# now Ctrl+C the app: the log shows the 5s pause, then "Waiting for active requests to complete",
# and the curl above still finishes with 200 and finishedDuringShutdown: true
```

The dev profile is the counter-example — same commands, cut connection:

```bash
mvn -pl project2 -am spring-boot:run -Dspring-boot.run.profiles=dev
```

Second instance, to see one drain while the other serves:

```bash
mvn -pl project2 -am spring-boot:run "-Dspring-boot.run.arguments=--server.port=8188 --management.server.port=9182"
```

## References

- [Spring Boot — Graceful Shutdown](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html) — official docs
- [Spring Boot — Kubernetes probes and application availability](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.kubernetes-probes)
- [Kubernetes — Pod termination](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination)
- Related, same topic folder: [Spring Boot Actuator](./01-spring-boot-actuator.md)
- Related, other topic folder: [@Async pitfalls](../concurrency/03-async-pitfalls.md), [Idempotency in REST APIs](../rest-api/04-idempotency-in-rest-apis.md)
