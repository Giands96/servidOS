package com.servidos.v1.reporting.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrdenVentasTest {

    @Test
    void ordenesDisponibles() {
        assertArrayEquals(
                new OrdenVentas[]{OrdenVentas.MAYOR_VENTA, OrdenVentas.MAS_RECIENTES, OrdenVentas.MAS_ANTIGUOS},
                OrdenVentas.values());
    }

    @Test
    void elOrdenPorDefectoSonLasMasRecientes() {
        assertEquals(OrdenVentas.MAS_RECIENTES, OrdenVentas.POR_DEFECTO);
    }
}
