package com.servidos.v1.ordering.application;

import java.math.BigDecimal;

/**
 * Vista mínima del catálogo para ordering: nombre snapshot + precio anti-tamper + disponibilidad.
 * Vive en ordering (dueño del puerto) para no acoplar el caso de uso al
 * dominio de catalog; el adapter traduce el {@code EstadoProducto}.
 * El nombre se congela en detalle_pedido.nombre_producto al crear el pedido.
 */
public record ProductoCatalogInfo(String nombre, BigDecimal precio, boolean disponible) {
}
