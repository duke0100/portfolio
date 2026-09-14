package com.example.project2.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#config
 * The knobs of the catalog snapshot fan-out, bound from the {@code catalog.snapshot.*} properties.
 *
 * <p>A virtual-thread-per-task executor has no pool size and no queue capacity to tune. The two
 * limits below replace them: how many tasks may touch the database at once, and how many may be
 * waiting for that turn.
 *
 * <p>Every value has a default here, so a test that shadows application.properties still gets a
 * sane limiter instead of a zero-permit one.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "catalog.snapshot")
public class CatalogSnapshotProperties {

    /** Tasks allowed to run at the same time. Each running task holds one database connection. */
    private int maxConcurrentTasks = 4;

    /** Tasks allowed to exist at all, running plus waiting. Over this the submit is rejected. */
    private int maxInFlightTasks = 64;

    /** How long a task waits for its turn before it gives up and reports the service as busy. */
    private Duration permitTimeout = Duration.ofSeconds(1);

    /** Budget for the whole fan-out. A part that misses it is dropped and the snapshot is partial. */
    private Duration timeout = Duration.ofSeconds(2);

    /** Rows the low-stock query may return, so the task can never load a whole category. */
    private int lowStockLimit = 10;
}
