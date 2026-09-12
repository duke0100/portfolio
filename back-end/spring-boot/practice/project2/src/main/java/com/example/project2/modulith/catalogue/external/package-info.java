/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#externalinternal-inside-a-module
 * (this package-info is the @NamedInterface sample in that section)
 *
 * <p>Everything in this package is the catalogue module's public API. Without this annotation the
 * package would be invisible to other modules, because only the module's base package is exported
 * by default.
 */
@NamedInterface("external")
package com.example.project2.modulith.catalogue.external;

import org.springframework.modulith.NamedInterface;
