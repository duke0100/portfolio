/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#core-common-the-shared-contract-layer
 * (this package is the core-common sample in that section)
 *
 * <p>The shared contract layer. Everything here is public on purpose: interfaces other modules
 * implement, event records they exchange, shared exceptions and small utilities. It depends on
 * nothing, so it can never take part in a cycle.
 */
package com.example.project2.modulith.core;
