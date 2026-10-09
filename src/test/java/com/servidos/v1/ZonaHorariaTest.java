package com.servidos.v1;

import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Los tests calculan "hoy" igual que la app: si el pom y V1Application se desalinean, falla. */
class ZonaHorariaTest {

    @Test
    void losTestsCorrenEnLaZonaDelNegocio() {
        assertEquals(V1Application.ZONA_HORARIA, TimeZone.getDefault().getID());
    }
}
