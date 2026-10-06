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
import java.util.Set;
import java.util.UUID;

/**
 * Palanca de acceso del tenant. Frena las escrituras con {@code 402} cuando el
 * restaurante no tiene acceso operativo, por dos motivos equivalentes desde el punto
 * de vista del cliente:
 * <ul>
 *   <li>la suscripción actual está {@code CANCELADA}, o</li>
 *   <li>el {@code restaurante.estado} es {@code INACTIVO} (la palanca que la plataforma
 *       usa para suspender: un cliente que no pagó).</li>
 * </ul>
 * <p>
 * Con esto el login sigue siendo válido y el token se emite: el restaurante conserva
 * acceso de <b>solo lectura</b> a sus datos, que es lo que necesita para ver qué tiene.
 * El frontend detecta la suspensión por {@code estado} en
 * {@code GET /api/v1/restaurantes/actual} y muestra el aviso de pago.
 * <p>
 * Pasan siempre: lecturas, auth, rutas fuera de {@code /api/}, sesiones de plataforma
 * (sin tenant) y el hatch de reactivación ({@code /renovar}).
 * <p>
 * <b>Las fechas sí bloquean</b>: una suscripción {@code ACTIVA} con
 * {@code fecha_fin} pasada frena las escrituras con 402. La fecha de fin es el día
 * inclusive: vence cuando ya pasó. Renovar (hatch) sigue pasando y arranca desde hoy,
 * así el que venció se recupera renovando.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_LECTURA = Set.of("GET", "HEAD", "OPTIONS");
    private static final String RENOVAR_PATH = "/api/v1/restaurantes/actual/suscripcion/renovar";

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
        if (restauranteRepository.existsByRestauranteIdAndEstado(restauranteId, EstadoRestaurante.INACTIVO)) {
            responderPagoRequerido(request, response,
                    "Restaurante suspendido: regularizá tu suscripción para seguir operando");
            return;
        }
        var actual = suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(restauranteId);
        if (actual.isPresent()) {
            var s = actual.get();
            if (s.getEstado() == EstadoSuscripcion.CANCELADA) {
                responderPagoRequerido(request, response,
                        "Suscripción cancelada: renová tu plan para seguir operando");
                return;
            }
            if (s.getEstado() == EstadoSuscripcion.ACTIVA && suscripcionVencida(s)) {
                responderPagoRequerido(request, response,
                        "Tu plan venció el " + s.getFechaFin() + ": renová para seguir operando");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * La fecha de fin es el día inclusive: hoy todavía opera, mañana ya no.
     * Sin fecha de fin no hay vencimiento posible.
     */
    private boolean suscripcionVencida(SuscripcionJpaEntity s) {
        return s.getFechaFin() != null && s.getFechaFin().isBefore(LocalDate.now());
    }

    private boolean dejaPasar(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            return true;
        }
        if (METODOS_LECTURA.contains(request.getMethod())) {
            return true;
        }
        if (uri.startsWith("/api/v1/auth/")) {
            return true;
        }
        // Un CANCELADA solo puede renovar: es su vía de reactivación.
        return uri.equals(RENOVAR_PATH);
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
