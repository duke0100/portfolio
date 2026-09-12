package com.example.project2.modulith;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#verifying-the-boundaries
 * (this class is the verification code sample in that section)
 *
 * <p>Pure static analysis over the bytecode - no Spring context, no database, so it runs in about
 * a second. This is the test that turns the architecture rules into a build failure.
 */
class ModulithArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(ModulithSlice.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.forEach(System.out::println);

        // Fails on a cycle, on a dependency not listed in allowedDependencies, or on any access to
        // another module's internal package.
        modules.verify();
    }
}
