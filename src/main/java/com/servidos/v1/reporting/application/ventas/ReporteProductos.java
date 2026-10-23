package com.servidos.v1.reporting.application.ventas;

import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.reporting.domain.RangoFechas;
import com.servidos.v1.reporting.domain.ResumenVentas;
import com.servidos.v1.reporting.domain.VentaProducto;

import java.util.List;

/** Productos vendidos en un período, del que más facturó al que menos, con el resumen de ventas. */
public record ReporteProductos(Periodo periodo, RangoFechas rango, ResumenVentas resumen,
                               List<VentaProducto> productos) {}
