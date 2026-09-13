package com.example.project2.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#config
 * The knobs of the catalog snapshot fan-out, bound from the {@code catalog.snapshot.*} properties.
 *
 * <p>Every value has a default here, so a test that shadows application.properties still gets a
 * sane pool instead of a zero-sized one.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "catalog.snapshot")
public class CatalogSnapshotProperties {

    /** Threads in the fan-out pool. Each running task holds one database connection. */
    private int poolSize = 4;

    /** Tasks that may wait for a thread. When it is full the caller runs the task itself. */
    private int queueCapacity = 64;

    /** Budget for the whole fan-out. A part that misses it is dropped and the snapshot is partial. */
    private Duration timeout = Duration.ofSeconds(2);

    /** Rows the low-stock query may return, so the task can never load a whole category. */
    private int lowStockLimit = 10;
}
