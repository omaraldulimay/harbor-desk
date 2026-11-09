package dev.harbordesk.domain;

import java.util.function.Supplier;

/** The tenant of the current request, bound to the thread by the web filter. */
public final class TenantContext {
    private static final ThreadLocal<TenantInfo> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static TenantInfo current() {
        return CURRENT.get();
    }

    public static TenantInfo require() {
        TenantInfo t = CURRENT.get();
        if (t == null) {
            throw new NoTenantException();
        }
        return t;
    }

    public static void set(TenantInfo tenant) {
        CURRENT.set(tenant);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T runAs(TenantInfo tenant, Supplier<T> work) {
        TenantInfo previous = CURRENT.get();
        CURRENT.set(tenant);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
