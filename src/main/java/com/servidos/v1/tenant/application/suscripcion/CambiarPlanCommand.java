package com.servidos.v1.tenant.application.suscripcion;

public record CambiarPlanCommand(Long restauranteId, Long nuevoPlanId, Boolean confirmado, String password) {}
