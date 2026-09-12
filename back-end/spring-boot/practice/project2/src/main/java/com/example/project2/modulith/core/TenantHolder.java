package com.example.project2.modulith.core;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#core-common-the-shared-contract-layer
 * (this is the shared utility sample in that section)
 *
 * <p>Holds the current tenant for the duration of a request. It is a utility, not a service, which
 * is exactly the kind of thing core-common is allowed to own.
 */
public final class TenantHolder {

    private static final ThreadLocal<String> CURRENT = ThreadLocal.withInitial(() -> "default");

    private TenantHolder() {
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void set(String tenant) {
        CURRENT.set(tenant);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
