package com.example.project2.client;

import com.example.project2.config.CatalogAsyncProperties;
import com.example.project2.dto.response.ShippingQuoteResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CompletableFuture;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#spring-integration
 * (this class is the "@Async returning CompletableFuture" code sample in that section)
 *
 * <p>The call itself is blocking. {@code @Async} moves it onto the catalog pool so the request
 * thread is free, and the method signature hands the caller a CompletableFuture it can compose
 * with the database stages.
 */
@Slf4j
@Component
public class ShippingQuoteClient {

    /** Assumed weight for a product nobody has measured, so the quote is never zero. */
    private static final BigDecimal DEFAULT_WEIGHT_KG = BigDecimal.ONE;

    private final RestClient restClient;
    private final CatalogAsyncProperties.Shipping shipping;
    private final MeterRegistry meterRegistry;

    public ShippingQuoteClient(CatalogAsyncProperties properties, MeterRegistry meterRegistry) {
        this.shipping = properties.getShipping();
        this.meterRegistry = meterRegistry;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // Both timeouts are mandatory. Without them one hung partner holds a pool thread forever.
        factory.setConnectTimeout(shipping.getConnectTimeout());
        factory.setReadTimeout(shipping.getReadTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(shipping.getBaseUrl())
                .build();
    }

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#timeouts-and-fallback
     * Asks the partner for a rate. It throws on any failure on purpose: the pipeline decides what a
     * missing quote means, this class does not.
     */
    @Async("catalogAsyncExecutor")
    public CompletableFuture<ShippingQuoteResponse> quoteAsync(Long productId, BigDecimal weightKg) {
        BigDecimal weight = weightKg == null ? DEFAULT_WEIGHT_KG : weightKg;
        PartnerRate rate = restClient.get()
                .uri("/rates?productId={productId}&weightKg={weightKg}", productId, weight)
                .retrieve()
                .body(PartnerRate.class);
        if (rate == null || rate.amount() == null) {
            throw new IllegalStateException("Shipping partner returned no rate for product " + productId);
        }
        meterRegistry.counter("catalog.shipping.quote", "source", "PARTNER").increment();
        return CompletableFuture.completedFuture(ShippingQuoteResponse.builder()
                .amount(rate.amount())
                .currency(rate.currency() == null ? shipping.getCurrency() : rate.currency())
                .source("PARTNER")
                .build());
    }

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#timeouts-and-fallback
     * The flat quote used when the partner is down or slow. It is marked FALLBACK so the caller can
     * tell an estimate from a real price.
     */
    public ShippingQuoteResponse fallbackQuote(BigDecimal weightKg) {
        BigDecimal weight = weightKg == null ? DEFAULT_WEIGHT_KG : weightKg;
        BigDecimal amount = shipping.getFallbackRatePerKg()
                .multiply(weight)
                .max(shipping.getFallbackMinimum())
                .setScale(2, RoundingMode.HALF_UP);
        meterRegistry.counter("catalog.shipping.quote", "source", "FALLBACK").increment();
        return ShippingQuoteResponse.builder()
                .amount(amount)
                .currency(shipping.getCurrency())
                .source("FALLBACK")
                .build();
    }

    /** The partner's wire format. */
    public record PartnerRate(BigDecimal amount, String currency) {
    }
}
