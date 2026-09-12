package com.servidos.v1.identity.application;

import com.servidos.v1.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Guardia anti-abuso de login (P1.8).
 *
 * <p>Cuenta solo fallos por (email, ip) en una ventana deslizante. Al superar el
 * máximo, bloquea temporalmente con duración progresiva (se duplica hasta un tope).
 * Todos los rechazos usan el mismo mensaje genérico para no enumerar emails:
 * un bloqueado es indistinguible de una credencial inválida.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = Logger.getLogger(LoginAttemptService.class.getName());

    static final int MAX_FALLOS = 5;
    static final long VENTANA_MILLIS = 10 * 60_000;
    static final long BLOQUEO_BASE_MILLIS = 5 * 60_000;
    static final long BLOQUEO_MAX_MILLIS = 60 * 60_000;

    private record Clave(String email, String ip) {}

    private static class Estado {
        int fallos;
        long ventanaInicio;
        long bloqueadoHasta;
        long proximoBloqueo = BLOQUEO_BASE_MILLIS;
    }

    private final Map<Clave, Estado> estados = new ConcurrentHashMap<>();

    /** Lanza BusinessException genérica si la clave está bloqueada. */
    public void exigirPermitido(String email, String ip) {
        Estado estado = estados.get(new Clave(email, ip));
        if (estado != null && System.currentTimeMillis() < estado.bloqueadoHasta) {
            throw new BusinessException("Credenciales inválidas");
        }
    }

    /** Registra un fallo; al llegar al máximo activa el bloqueo progresivo. */
    public void registrarFallo(String email, String ip) {
        long ahora = System.currentTimeMillis();
        estados.compute(new Clave(email, ip), (clave, previo) -> {
            Estado estado = previo != null ? previo : new Estado();
            if (previo == null || ahora - estado.ventanaInicio >= VENTANA_MILLIS) {
                estado.fallos = 1;
                estado.ventanaInicio = ahora;
            } else {
                estado.fallos++;
            }
            if (estado.fallos >= MAX_FALLOS) {
                estado.bloqueadoHasta = ahora + estado.proximoBloqueo;
                estado.proximoBloqueo = Math.min(estado.proximoBloqueo * 2, BLOQUEO_MAX_MILLIS);
                estado.fallos = 0;
            }
            return estado;
        });
        // Nunca se loguea la contraseña, solo email + ip para auditoría.
        log.warning("Login fallido email=" + email + " ip=" + ip);
    }

    /** Login exitoso: la clave vuelve a foja cero. */
    public void limpiar(String email, String ip) {
        estados.remove(new Clave(email, ip));
    }
}
