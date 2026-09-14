package com.example.project2.service.impl;

import com.example.project2.config.CatalogAsyncProperties;
import com.example.project2.dto.response.ProductPriceAuditResponse;
import com.example.project2.entity.ProductPriceAudit;
import com.example.project2.event.ProductPriceChanged;
import com.example.project2.repository.ProductPriceAuditRepository;
import com.example.project2.service.ProductPriceAuditService;
import com.example.project2.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#after-commit-listener
 * (this class is the transactional half of the after-commit audit sample in that section)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductPriceAuditServiceImpl implements ProductPriceAuditService {

    private final ProductPriceAuditRepository auditRepository;
    private final CatalogAsyncProperties properties;

    /**
     * REQUIRES_NEW rather than REQUIRED, because the caller's transaction already committed and
     * the async hop gave us a thread with no transaction on it at all. Being explicit stops anyone
     * assuming this write joins the price change.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ProductPriceChanged event) {
        ProductPriceAudit audit = ProductPriceAudit.builder()
                .productId(event.productId())
                .oldPrice(event.oldPrice())
                .newPrice(event.newPrice())
                .changedBy(event.changedBy())
                // Present only because the TaskDecorator copied MDC onto this pool thread.
                .correlationId(MDC.get(CorrelationIdFilter.MDC_KEY))
                .workerThread(Thread.currentThread().getName())
                .recordedAt(Instant.now())
                .build();

        auditRepository.save(audit);
        log.info("Audited price change {} -> {} for product {} on thread {}",
                event.oldPrice(), event.newPrice(), event.productId(), audit.getWorkerThread());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductPriceAuditResponse> recentChanges(Long productId) {
        return auditRepository
                .findByProductIdOrderByRecordedAtDescIdDesc(
                        productId, PageRequest.ofSize(properties.getAuditHistoryLimit()))
                .stream()
                .map(audit -> new ProductPriceAuditResponse(
                        audit.getId(),
                        audit.getProductId(),
                        audit.getOldPrice(),
                        audit.getNewPrice(),
                        audit.getChangedBy(),
                        audit.getCorrelationId(),
                        audit.getWorkerThread(),
                        audit.getRecordedAt()))
                .toList();
    }
}
