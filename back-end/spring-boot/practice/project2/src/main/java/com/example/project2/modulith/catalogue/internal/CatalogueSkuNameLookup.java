package com.example.project2.modulith.catalogue.internal;

import com.example.project2.modulith.core.SkuNameLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#core-common-the-shared-contract-layer
 * (this class is the "module A implements the contract" sample in that section)
 *
 * <p>Catalogue publishes a core-common contract as a bean. Loyalty injects the interface and gets
 * catalogue data without a compile-time dependency on catalogue.
 */
@Component
@RequiredArgsConstructor
class CatalogueSkuNameLookup implements SkuNameLookup {

    private final CatalogueService catalogueService;

    @Override
    public Optional<String> findNameBySku(String sku) {
        return catalogueService.find(sku).map(item -> item.name());
    }
}
