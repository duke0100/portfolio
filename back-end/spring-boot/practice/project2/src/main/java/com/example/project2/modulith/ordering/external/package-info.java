/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#externalinternal-inside-a-module
 * (this package-info is a @NamedInterface sample in that section)
 *
 * <p>Public API of the ordering module: the facade plus the command and receipt records it speaks.
 */
@NamedInterface("external")
package com.example.project2.modulith.ordering.external;

import org.springframework.modulith.NamedInterface;
