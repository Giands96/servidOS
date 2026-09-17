package com.servidos.v1.identity.domain;

/**
 * Vocabulario de dominio para estado de rol.
 * Nota P1.6: las tablas {@code rol_restaurante}/{@code rol_plataforma} usan
 * String con 'ACTIVO' (V1__init.sql). No comparar directamente con este enum
 * en login/refresh; usar "ACTIVO".equalsIgnoreCase(estado).
 */
public enum EstadoRol {
    HABILITADO,
    DESHABILITADO
}
