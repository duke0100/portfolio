package com.example.project2.modulith.core;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#core-common-the-shared-contract-layer
 * (this is the shared exception sample in that section)
 *
 * <p>Thrown when a module is asked for something a caller had no business asking for, such as an
 * unknown SKU. Shared here so every module throws the same type and one advice can map it.
 */
public class ModuleContractException extends RuntimeException {

    public ModuleContractException(String message) {
        super(message);
    }
}
