/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#one-way-dependencies
 * (this package-info is the allowedDependencies sample in that section)
 *
 * <p>Catalogue is a leaf module: it may only reach down into core-common, never sideways into
 * ordering or loyalty. Modulith fails the build if that changes.
 */
@ApplicationModule(displayName = "Catalogue", allowedDependencies = "core")
package com.example.project2.modulith.catalogue;

import org.springframework.modulith.ApplicationModule;
