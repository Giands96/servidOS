package com.servidos.v1.tenant.application.suscripcion;

/** Solo la plataforma cambia el plan: el cobro del plan nuevo se recibe fuera del sistema. */
public record CambiarPlanCommand(Long restauranteId, Long nuevoPlanId) {}
