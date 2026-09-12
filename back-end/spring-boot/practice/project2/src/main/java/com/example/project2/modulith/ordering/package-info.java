/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#one-way-dependencies
 * (this package-info is the named-interface dependency sample in that section)
 *
 * <p>Ordering may call catalogue, but only through its {@code external} named interface. It is not
 * allowed to see loyalty at all - that direction goes over an event instead.
 */
@ApplicationModule(displayName = "Ordering", allowedDependencies = {"core", "catalogue :: external"})
package com.example.project2.modulith.ordering;

import org.springframework.modulith.ApplicationModule;
