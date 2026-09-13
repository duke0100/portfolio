package com.example.project2.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#config
 * The knobs of the async pricing pipeline, bound from the {@code catalog.async.*} properties.
 *
 * <p>Every value has a default here, so a test that shadows application.properties still gets a
 * usable pool and a real timeout instead of zero.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "catalog.async")
public class CatalogAsyncProperties {

    /** Threads in the async pool. Four of the five stages run a query, so each one takes a connection. */
    private int poolSize = 4;

    /** Stages that may wait for a thread. When it is full the caller runs the stage itself. */
    private int queueCapacity = 128;

    /** Budget for the whole pipeline. {@code orTimeout} fails the response once it is gone. */
    private Duration timeout = Duration.ofSeconds(3);

    /** Rows the related-products stage may return, so one huge category cannot flood the response. */
    private int relatedLimit = 5;

    /** Ids one batch call may ask for. Each id costs five stages, so this caps the fan-out. */
    private int maxBatchSize = 20;

    private Shipping shipping = new Shipping();

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#timeouts-and-fallback
     * Settings for the outbound shipping-partner call and for the quote used when it is down.
     */
    @Getter
    @Setter
    public static class Shipping {

        /** The partner service. Nothing listens here locally, which is what exercises the fallback. */
        private String baseUrl = "http://localhost:9099";

        /** How long we wait for the TCP connect before giving up on the partner. */
        private Duration connectTimeout = Duration.ofMillis(500);

        /** How long we wait for the partner's response body. */
        private Duration readTimeout = Duration.ofMillis(800);

        /** Per-stage budget. {@code completeOnTimeout} swaps in the flat quote once it is gone. */
        private Duration quoteTimeout = Duration.ofSeconds(1);

        /** Flat rate used when the partner fails, so the page still shows a shipping price. */
        private BigDecimal fallbackRatePerKg = new BigDecimal("2.50");

        /** Floor for the flat rate, because a 50g item still costs something to ship. */
        private BigDecimal fallbackMinimum = new BigDecimal("4.90");

        private String currency = "USD";
    }
}
