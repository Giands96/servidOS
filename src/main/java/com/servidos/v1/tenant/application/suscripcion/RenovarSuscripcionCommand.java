package com.servidos.v1.tenant.application.suscripcion;

/** {@code planId} es opcional: si viene null se renueva el plan de la última suscripción. */
public record RenovarSuscripcionCommand(Long restauranteId, Long planId) {}
