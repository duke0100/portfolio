package com.example.project2.actuator;

import com.example.project2.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Interview topic: docs/interview/spring-boot/01-spring-boot-actuator.md#custom-health-indicator
 * Custom health check that shows up as "productCatalog" under {@code /actuator/health}. It just
 * reads one row instead of counting the table, since counting ~1M rows would be far too slow
 * for a check a load balancer calls every few seconds.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCatalogHealthIndicator implements HealthIndicator {

    /** Above this the catalog still reports UP, but the detail flags it as slow. */
    private static final long SLOW_PROBE_MILLIS = 500L;

    private final ProductRepository productRepository;

    @Override
    public Health health() {
        long startNanos = System.nanoTime();
        try {
            boolean hasProducts = productRepository.findAll(PageRequest.of(0, 1)).hasContent();
            long tookMillis = (System.nanoTime() - startNanos) / 1_000_000;

            // Slow is not the same as down - reporting DOWN here would needlessly pull this
            // instance out of the load balancer during a temporary database hiccup.
            return Health.up()
                    .withDetail("probeMillis", tookMillis)
                    .withDetail("slow", tookMillis >= SLOW_PROBE_MILLIS)
                    .withDetail("catalogEmpty", !hasProducts)
                    .build();
        } catch (Exception e) {
            log.warn("Product catalog health probe failed", e);
            return Health.down(e).build();
        }
    }
}
