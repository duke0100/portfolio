package com.example.project2.modulith.catalogue.internal;

import com.example.project2.modulith.catalogue.external.CatalogueFacade;
import com.example.project2.modulith.catalogue.external.CatalogueItem;
import com.example.project2.modulith.core.ModuleContractException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#externalinternal-inside-a-module
 * (this class is the closed-implementation sample in that section)
 *
 * <p>Lives in {@code internal}, so no other module can inject it by type even though the bean is in
 * the same application context. A real catalogue would read from a repository here; the fixed map
 * keeps the slice runnable without extra tables.
 */
@Service
class CatalogueService implements CatalogueFacade {

    private static final Map<String, CatalogueItem> ITEMS = Map.of(
            "SKU-KEYBOARD", new CatalogueItem("SKU-KEYBOARD", "Mechanical keyboard", new BigDecimal("89.00")),
            "SKU-MONITOR", new CatalogueItem("SKU-MONITOR", "27 inch monitor", new BigDecimal("245.50")),
            "SKU-MOUSE", new CatalogueItem("SKU-MOUSE", "Wireless mouse", new BigDecimal("29.90")));

    @Override
    public CatalogueItem requireItem(String sku) {
        return find(sku).orElseThrow(() -> new ModuleContractException("Unknown SKU: " + sku));
    }

    Optional<CatalogueItem> find(String sku) {
        return Optional.ofNullable(ITEMS.get(sku));
    }
}
