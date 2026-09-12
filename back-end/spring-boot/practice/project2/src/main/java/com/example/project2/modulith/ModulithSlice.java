package com.example.project2.modulith;

import org.springframework.modulith.Modulithic;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#main-module-aggregator
 * (this class is the {@code @Modulithic} code sample in that section)
 *
 * <p>Marks the root of the modulith slice: every direct sub-package below is one application
 * module. In a greenfield app this annotation would sit on the {@code @SpringBootApplication}
 * class instead; here it sits on its own marker so the slice can live next to project2's
 * pre-existing layered packages without dragging them into the verification.
 */
@Modulithic(systemName = "project2-modulith-slice")
public final class ModulithSlice {

    private ModulithSlice() {
    }
}
