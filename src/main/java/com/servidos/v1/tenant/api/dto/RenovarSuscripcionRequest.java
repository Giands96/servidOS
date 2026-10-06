package com.servidos.v1.tenant.api.dto;

/**
 * Renovación hecha por la plataforma después de cobrar fuera del sistema.
 *
 * @param planId opcional. Obligatorio solo si el restaurante todavía no tiene ninguna
 *               suscripción; si no viene, se renueva el plan de la última.
 */
public record RenovarSuscripcionRequest(Long planId) {}
