package com.servidos.v1.tenant.application.suscripcion;

import com.servidos.v1.tenant.domain.Suscripcion;

/**
 * Suscripción con el nombre de su plan resuelto en la misma consulta.
 *
 * <p>Existe porque el nombre del plan no es una columna de {@code suscripcion}: vive
 * en {@code plan.nombre_plan}. Sin esta proyección, el endpoint de suscripción del
 * tenant devolvía solo {@code planId} y el frontend no tenía con qué mostrar "Estás en
 * Estándar". Traer el nombre en el mismo SELECT evita el N+1 de un segundo lookup y
 * evita que el cliente tenga que cruzarlo con el catálogo de planes.
 *
 * @param suscripcion dominio de la suscripción, con su monto y moneda ya snapshot
 * @param nombrePlan  nombre del plan; null solo si la suscripción apunta a un plan borrado
 */
public record SuscripcionConPlan(Suscripcion suscripcion, String nombrePlan) {
}