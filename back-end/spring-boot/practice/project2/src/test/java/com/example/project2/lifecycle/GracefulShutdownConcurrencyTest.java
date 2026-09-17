package com.example.project2.lifecycle;

import com.example.project2.Project2Application;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Interview topic: docs/interview/spring-boot/04-graceful-shutdown.md#under-load
 * (this is the concurrency test quoted in that section)
 *
 * <p>Eight slow requests are in flight when the context closes. The invariant is that all eight
 * still get a 200, and that a caller arriving afterwards is refused at the socket.
 *
 * <p>It starts its own context instead of using {@code @SpringBootTest}, because the test closes
 * the context on purpose and the test framework would try to reuse and restart a cached one. The
 * database URL is its own H2 instance for the same reason: {@code ddl-auto=create-drop} drops the
 * schema on close, and the shared test database is still in use by other classes.
 *
 * <p>{@code context.close()} is what SIGTERM triggers, so this covers the Spring half of the
 * sequence. The JVM shutdown hook and the SIGKILL that follows an expired pod grace period need a
 * real process, not a test.
 */
class GracefulShutdownConcurrencyTest {

    private static final int CALLERS = 8;
    private static final long HOLD_MILLIS = 2000;
    private static final long AWAIT_SECONDS = 20;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private ConfigurableApplicationContext context;

    @AfterEach
    void closeContextIfTheTestFailedEarly() {
        if (context != null && context.isActive()) {
            context.close();
        }
    }

    @Test
    void shutdown_finishesInFlightRequests_andRefusesNewConnections() throws Exception {
        context = startApplication();
        int port = Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));
        MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);

        ExecutorService callers = Executors.newFixedThreadPool(CALLERS);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> inFlight = new ArrayList<>();
        try {
            for (int i = 0; i < CALLERS; i++) {
                inFlight.add(callers.submit(() -> {
                    startGun.await(); // every caller waits here, then they all go at once
                    return restTemplate.getForEntity(
                            url(port, "/api/v1/lifecycle/drain-probe?holdMillis=" + HOLD_MILLIS), String.class);
                }));
            }
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

                JsonNode data = objectMapper.readTree(response.getBody()).get("data");
                assertThat(data.get("actualHoldMillis").asLong()).isGreaterThanOrEqualTo(HOLD_MILLIS);
                // The request outlived the start of the shutdown, which is the whole point.
                assertThat(data.get("finishedDuringShutdown").asBoolean()).isTrue();
            }

            assertThat(context.isActive()).isFalse();
            // Nothing is listening any more, so a late caller is refused at the socket instead of
            // being served by a half-closed context.
            assertThatThrownBy(() -> restTemplate.getForEntity(url(port, "/api/v1/lifecycle/state"), String.class))
                    .isInstanceOf(ResourceAccessException.class);
        } finally {
            callers.shutdownNow();
        }
    }

    private ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(Project2Application.class)
                .properties(Map.of(
                        "server.port", "0",
                        "management.server.port", "0",
                        "server.shutdown", "graceful",
                        "spring.lifecycle.timeout-per-shutdown-phase", "20s",
                        "catalog.shutdown.deregistration-delay", "PT0.2S",
                        "catalog.shutdown.drain-log-interval", "PT0.1S",
                        "catalog.shutdown.max-probe-hold", "PT10S",
                        // Its own H2 instance, so create-drop cannot pull the schema out from
                        // under the contexts the other test classes have cached.
                        "spring.datasource.url", "jdbc:h2:mem:project2_shutdown_test;DB_CLOSE_DELAY=-1"))
                .run();
    }

    /**
     * Waits on the probe gauge rather than sleeping: the shutdown has to start while all eight
     * requests are genuinely inside the handler, otherwise the test proves nothing.
     */
    private void awaitActiveProbes(MeterRegistry meterRegistry) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (activeProbes(meterRegistry) >= CALLERS) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        throw new AssertionError("Only " + activeProbes(meterRegistry) + " of " + CALLERS
                + " probes were in flight before the deadline");
    }

    private long activeProbes(MeterRegistry meterRegistry) {
        LongTaskTimer timer = meterRegistry.find("catalog.shutdown.probe.active").longTaskTimer();
        return timer == null ? 0 : timer.activeTasks();
    }

    private String url(int port, String path) {
        return "http://localhost:" + port + path;
    }
}
