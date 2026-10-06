package com.servidos.v1.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cancelación de la suscripción. Exige la contraseña del usuario en sesión, igual
 * que renovar y cambiar plan: cancelar corta las escrituras del restaurante con 402,
 * así que no puede quedar a un clic de una sesión abierta o secuestrada.
 */
public record CancelarSuscripcionRequest(@NotBlank String password) {}
