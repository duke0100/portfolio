package com.example.project2.modulith.catalogue.external;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#facade-sync
 * (this interface is the facade code sample in that section)
 *
 * <p>The only way into the catalogue module. Callers get a plain in-JVM method call; the
 * implementation, the repository and the domain type stay in {@code internal}.
 */
public interface CatalogueFacade {

    /** Throws {@code ModuleContractException} when the SKU does not exist. */
    CatalogueItem requireItem(String sku);
}
