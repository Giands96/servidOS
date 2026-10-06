package com.servidos.v1.shared.security;

import tools.jackson.databind.ObjectMapper;
import com.servidos.v1.shared.exception.ErrorResponse;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * Palanca de acceso del tenant. Con el restaurante <b>bloqueado</b> las escrituras
 * responden {@code 402}, para que el frontend muestre el aviso de pago. Bloqueado es:
 * <ul>
 *   <li>{@code restaurante.estado = INACTIVO} (la plataforma lo suspendió), o</li>
 *   <li>no tiene suscripción actual, o</li>
 *   <li>la suscripción actual ya venció ({@code fecha_fin} pasada).</li>
 * </ul>
 * Una suscripción {@code CANCELADA} no bloquea por sí misma: cancelar es "no renovar",
 * así que el restaurante opera hasta su {@code fecha_fin}. La fecha de fin es el día
 * inclusive: hoy todavía opera, mañana ya no.
 * <p>
 * Bloqueado = solo lectura. Pasan siempre: lecturas, auth, rutas fuera de {@code /api/}
 * y sesiones de plataforma (sin tenant). Además, para no dejar pedidos colgados, con el
 * restaurante bloqueado se pueden <b>cerrar los pedidos en curso</b>: avanzar o cancelar
 * su estado, marcarlos listos, cobrarlos y reembolsarlos. Crear pedidos nuevos y
 * cualquier otra escritura siguen bloqueados.
 * <p>
 * Renovar no es self-service: lo hace la plataforma ({@code POST /restaurantes/{id}/suscripcion/renovar}).
 */
@Component
@RequiredArgsConstructor
public class SubscriptionFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_LECTURA = Set.of("GET", "HEAD", "OPTIONS");

    /** Escrituras que cierran un pedido ya creado; pasan aunque el restaurante esté bloqueado. */
    private static final List<Pattern> CIERRE_DE_PEDIDOS = List.of(
            Pattern.compile("PATCH /api/v1/pedidos/\\d+/estado"),
            Pattern.compile("POST /api/v1/pedidos/\\d+/confirmar"),
            Pattern.compile("POST /api/v1/cocina/pedidos/\\d+/listo"),
            Pattern.compile("POST /api/v1/pagos"),
            Pattern.compile("POST /api/v1/pagos/\\d+/reembolso"));

    private static final String CONTACTO = "contactá a la plataforma para regularizar tu suscripción";

    private final SuscripcionJpaRepository suscripcionRepository;
    private final RestauranteJpaRepository restauranteRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (dejaPasar(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        Long restauranteId = TenantContext.getRestauranteId();
        if (restauranteId == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String motivo = motivoDeBloqueo(restauranteId);
        if (motivo != null && !esCierreDePedido(request)) {
            responderPagoRequerido(request, response, motivo);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Devuelve el mensaje de bloqueo, o {@code null} si el restaurante opera normal. */
    private String motivoDeBloqueo(Long restauranteId) {
        if (restauranteRepository.existsByRestauranteIdAndEstado(restauranteId, EstadoRestaurante.INACTIVO)) {
            return "Restaurante suspendido: " + CONTACTO;
        }
        LocalDate hoy = LocalDate.now();
        var actual = suscripcionRepository
                .findTopByRestauranteIdAndFechaInicioLessThanEqualOrderBySuscripcionIdDesc(restauranteId, hoy);
        if (actual.isEmpty()) {
            return "Restaurante sin suscripción: " + CONTACTO;
        }
        SuscripcionJpaEntity s = actual.get();
        if (s.getFechaFin() != null && s.getFechaFin().isBefore(hoy)) {
            return (s.getEstado() == EstadoSuscripcion.CANCELADA
                    ? "Tu suscripción cancelada terminó el " : "Tu plan venció el ")
                    + s.getFechaFin() + ": " + CONTACTO;
        }
        return null;
    }

    private boolean esCierreDePedido(HttpServletRequest request) {
        String operacion = request.getMethod() + " " + request.getRequestURI();
        return CIERRE_DE_PEDIDOS.stream().anyMatch(p -> p.matcher(operacion).matches());
    }

    private boolean dejaPasar(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            return true;
        }
        if (METODOS_LECTURA.contains(request.getMethod())) {
            return true;
        }
        return uri.startsWith("/api/v1/auth/");
    }

    private void responderPagoRequerido(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                        String mensaje) throws IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.PAYMENT_REQUIRED.value() + " " + HttpStatus.PAYMENT_REQUIRED.getReasonPhrase())
                .message(mensaje)
                .path(request.getRequestURI())
                .traceID(traceId)
                .build();
        response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
