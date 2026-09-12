/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#externalinternal-inside-a-module
 * (this package-info is a @NamedInterface sample in that section)
 *
 * <p>Loyalty only exposes a read facade; the listener that fills the balance stays closed.
 */
@NamedInterface("external")
package com.example.project2.modulith.loyalty.external;

import org.springframework.modulith.NamedInterface;
