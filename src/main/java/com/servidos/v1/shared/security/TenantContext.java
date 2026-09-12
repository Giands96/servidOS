package com.servidos.v1.shared.security;

public class TenantContext {
    private static final ThreadLocal<Long> currentTenant = new ThreadLocal<>();

    public static void setRestauranteId(Long restauranteId) {
        currentTenant.set(restauranteId);
    }

    public static Long getRestauranteId() {
        return currentTenant.get();
    }

    public static void clear() {
        currentTenant.remove();
    }

}
