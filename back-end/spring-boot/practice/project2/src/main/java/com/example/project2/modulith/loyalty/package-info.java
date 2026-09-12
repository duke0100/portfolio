/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#one-way-dependencies
 * (this package-info is the "listener module" sample in that section)
 *
 * <p>Loyalty reacts to ordering's event but is not allowed to import ordering or catalogue. The
 * catalogue data it needs arrives through a core-common contract instead.
 */
@ApplicationModule(displayName = "Loyalty", allowedDependencies = "core")
package com.example.project2.modulith.loyalty;

import org.springframework.modulith.ApplicationModule;
