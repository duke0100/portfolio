package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.request.ChangeProductPriceRequest;
import com.example.project2.dto.response.AsyncDispatchProbeResponse;
import com.example.project2.dto.response.ProductPriceAuditResponse;
import com.example.project2.dto.response.ProductPriceChangeResponse;
import com.example.project2.service.AsyncDispatchProbeService;
import com.example.project2.service.ProductPriceAuditService;
import com.example.project2.service.ProductPriceChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#endpoints
 * (these three endpoints are the ones quoted in that section)
 *
 * <p>The price change returns as soon as it commits. The audit row shows up a moment later, which
 * is the whole point of doing it on the async pool.
 */
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class ProductPriceChangeController {

    private final ProductPriceChangeService priceChangeService;
    private final ProductPriceAuditService auditService;
    private final AsyncDispatchProbeService dispatchProbeService;

    /**
     * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#the-price-change
     * POST, not PUT: the same body sent twice is a second price change, not the same one.
     */
    @PostMapping("/products/{productId}/price-changes")
    public ApiResponse<ProductPriceChangeResponse> changePrice(@PathVariable Long productId,
                                                              @Valid @RequestBody ChangeProductPriceRequest request) {
        return ApiResponse.ok(priceChangeService.changePrice(productId, request));
    }

    /** The audit trail written after commit. Capped by catalog.async.audit-history-limit. */
    @GetMapping("/products/{productId}/price-changes")
    public ApiResponse<List<ProductPriceAuditResponse>> priceHistory(@PathVariable Long productId) {
        return ApiResponse.ok(auditService.recentChanges(productId));
    }

    /**
     * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#self-invocation
     * Smoke check for the async wiring. If selfInvocationRanAsync and proxiedRanAsync are both
     * false, @Async is off in this deployment and everything is running on request threads.
     */
    @GetMapping("/async/dispatch-probe")
    public ApiResponse<AsyncDispatchProbeResponse> dispatchProbe() {
        return ApiResponse.ok(dispatchProbeService.probe());
    }
}
