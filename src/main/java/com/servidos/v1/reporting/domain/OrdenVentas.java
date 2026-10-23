package com.servidos.v1.reporting.domain;

/** Orden del listado de ventas; desempata siempre por pedido para que sea determinista. */
public enum OrdenVentas {
    /** Mayor total primero. */
    MAYOR_VENTA,
    MAS_RECIENTES,
    MAS_ANTIGUOS;

    public static final OrdenVentas POR_DEFECTO = MAS_RECIENTES;
}
