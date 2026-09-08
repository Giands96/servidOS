package com.servidos.v1.shared.security;


public class CurrentUser {
    public static final ThreadLocal<Long> currentUser = new ThreadLocal<Long>();
    public static final ThreadLocal<String> currentRole = new ThreadLocal<String>();

    public static void setCurrentUser(Long userId) {
        currentUser.set(userId);
    }

    public static void setRole(String role) {
        currentRole.set(role);
    }

    public static Long getCurrentUser() {
        return currentUser.get();
    }

    public static String getRole() {
        return currentRole.get();
    }

    public static void clear() {
        currentUser.remove();
        currentRole.remove();
    }
}
