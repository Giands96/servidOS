package com.servidos.v1.identity.application;

import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    private final LoginAttemptService guard = new LoginAttemptService();

    @Test
    void permiteAntesDelMaximoYBloqueaAlLlegar() {
        String email = "ataque@demo.pe";
        String ip = "10.0.0.1";
        for (int i = 0; i < LoginAttemptService.MAX_FALLOS; i++) {
            assertDoesNotThrow(() -> guard.exigirPermitido(email, ip));
            guard.registrarFallo(email, ip);
        }
        BusinessException ex = assertThrows(
                BusinessException.class, () -> guard.exigirPermitido(email, ip));
        assertEquals("Credenciales inválidas", ex.getMessage());
    }

    @Test
    void exitoLimpiaElContador() {
        String email = "bueno@demo.pe";
        String ip = "10.0.0.2";
        guard.registrarFallo(email, ip);
        guard.limpiar(email, ip);
        assertDoesNotThrow(() -> guard.exigirPermitido(email, ip));
    }

    @Test
    void otraIpNoSeVeAfectada() {
        guard.registrarFallo("x@demo.pe", "10.0.0.3");
        assertDoesNotThrow(() -> guard.exigirPermitido("x@demo.pe", "10.0.0.99"));
    }
}
