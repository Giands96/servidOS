package com.servidos.v1.shared.security;


public class CurrentUser {
    public static final ThreadLocal<Long> currentUser = new ThreadLocal<Long>();
    public static final ThreadLocal<Long> currentRole = new ThreadLocal<Long>();

    public static void setCurrentUser(Long userId) {
        currentUser.set(userId);
    }

    public static void setRole(Long roleId) {
        currentRole.set(roleId);
    }

    public static Long getCurrentUser() {
        return currentUser.get();
    }

    public static Long getRole() {
        return currentRole.get();
    }

    public static void clear() {
        currentUser.remove();
        currentRole.remove();
    }
}
