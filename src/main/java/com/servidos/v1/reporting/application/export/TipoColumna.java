package com.servidos.v1.reporting.application.export;

/** Tipo de dato de una columna; decide el valor esperado en las filas y cómo se muestra. */
public enum TipoColumna {
    /** {@link String}. */
    TEXTO,
    /** {@link Number}. */
    ENTERO,
    /** {@link java.math.BigDecimal}, en soles. */
    MONEDA,
    /** {@link java.time.LocalDateTime}. */
    FECHA_HORA
}
