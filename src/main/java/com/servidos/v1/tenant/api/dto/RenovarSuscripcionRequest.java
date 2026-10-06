package com.servidos.v1.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Renovación de la suscripción. Exige la contraseña del usuario en sesión porque
 * renovar crea una obligación de pago nueva: no puede ser un clic casual que un
 * restaurante vencido use para auto-devolverse el acceso sin haber pagado.
 *
 * <p>No verifica el pago (el cobro se resuelve fuera del sistema); lo que garantiza
 * es que la renovación sea un acto deliberado y atribuible del ADMINISTRADOR.
 */
public record RenovarSuscripcionRequest(@NotBlank String password) {}