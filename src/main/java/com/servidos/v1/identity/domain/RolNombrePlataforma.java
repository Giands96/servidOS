package com.servidos.v1.identity.domain;

/**
 * Vocabulario cerrado de roles de plataforma.
 * Mantiene los nombres actuales en DB (SUPERADMIN, ADMIN, MODERADOR).
 * La tabla {@code rol_plataforma} sigue existiendo; este enum es la
 * fuente de verdad del vocabulario para evitar strings mágicos.
 */
public enum RolNombrePlataforma {
    SUPERADMIN(3),
    ADMIN(2),
    MODERADOR(1);

    private final int rango;

    RolNombrePlataforma(int rango) {
        this.rango = rango;
    }

    public int getRango() {
        return rango;
    }

    public static boolean esValido(String nombre) {
        return rangoDe(nombre) > 0;
    }

    public static int rangoDe(String nombre) {
        if (nombre == null) {
            return 0;
        }
        try {
            return valueOf(nombre.trim().toUpperCase()).rango;
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /**
     * Jerarquía de plataforma: un rol solo otorga, y solo gestiona a usuarios con, un rol
     * estrictamente menor al suyo. SUPERADMIN maneja ADMIN y MODERADOR; ADMIN (socio, con
     * las mismas funciones que SUPERADMIN) solo MODERADOR, así que no crea otros ADMIN.
     * SUPERADMIN nunca se otorga ni se toca por la API (el primero viene del bootstrap, V9).
     */
    public static boolean puedeGestionar(String rolActor, String rol) {
        int rangoRol = rangoDe(rol);
        return rangoRol > 0 && rangoRol < rangoDe(rolActor);
    }
}
