package com.servidos.v1.identity.domain;

/**
 * Vocabulario cerrado de roles de restaurante.
 * Mantiene los nombres actuales en DB (ADMINISTRADOR como admin del
 * tenant, RECEPCION ya en uso en cocina) y suma los operativos
 * previstos sin activar permisos hasta que cada módulo los exija.
 * La tabla {@code rol_restaurante} sigue existiendo; este enum es la
 * fuente de verdad del vocabulario para evitar strings mágicos.
 */
public enum RolNombreRestaurante {
    ADMINISTRADOR,
    RECEPCION,
    COCINERO,
    MESERO,
    CAJERO,
    REPARTIDOR;

    public static boolean esValido(String nombre) {
        if (nombre == null) {
            return false;
        }
        try {
            valueOf(nombre.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean esAdministrador(String nombre) {
        return ADMINISTRADOR.name().equalsIgnoreCase(nombre == null ? null : nombre.trim());
    }
}
