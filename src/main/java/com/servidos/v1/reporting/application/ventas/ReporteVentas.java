package com.servidos.v1.reporting.application.ventas;

import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.reporting.domain.RangoFechas;
import com.servidos.v1.reporting.domain.ResumenVentas;
import com.servidos.v1.reporting.domain.VentaPedido;
import org.springframework.data.domain.Page;

/** Ventas de un período: resumen y una página de pedidos. */
public record ReporteVentas(Periodo periodo, RangoFechas rango, ResumenVentas resumen, Page<VentaPedido> ventas) {}
