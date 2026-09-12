package com.example.project2.modulith.catalogue.external;

import java.math.BigDecimal;

/** Public DTO of the catalogue module - callers never see the internal domain type. */
public record CatalogueItem(String sku, String name, BigDecimal unitPrice) {
}
