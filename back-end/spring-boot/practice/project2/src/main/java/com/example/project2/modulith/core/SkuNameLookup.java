package com.example.project2.modulith.core;

import java.util.Optional;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#core-common-the-shared-contract-layer
 * (this interface is the shared-contract code sample in that section)
 *
 * <p>Contract for reading a product name by SKU. The catalogue module implements it and the
 * loyalty module injects it, so loyalty gets catalogue data without depending on catalogue.
 */
public interface SkuNameLookup {

    Optional<String> findNameBySku(String sku);
}
