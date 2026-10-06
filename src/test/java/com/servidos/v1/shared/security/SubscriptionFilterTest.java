package com.servidos.v1.shared.security;

import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionFilterTest {

    @Mock SuscripcionJpaRepository suscripcionRepository;
    @Mock RestauranteJpaRepository restauranteRepository;
    @Mock FilterChain chain;

    SubscriptionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SubscriptionFilter(suscripcionRepository, restauranteRepository,
                new tools.jackson.databind.ObjectMapper());
        TenantContext.setRestauranteId(7L);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private HttpServletRequest request(String method, String uri) {
        var req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getRequestURI()).thenReturn(uri);
        return req;
    }

    private HttpServletResponse response() throws Exception {
        var res = mock(HttpServletResponse.class);
        // Lenient: solo el caso 402 llama a getWriter().
        lenient().when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        return res;
    }

    private SuscripcionJpaEntity suscripcion(EstadoSuscripcion estado) {
        var e = new SuscripcionJpaEntity();
        e.setEstado(estado);
        return e;
    }

    private void restauranteActivo() {
        when(restauranteRepository.existsByRestauranteIdAndEstado(7L, EstadoRestaurante.INACTIVO))
                .thenReturn(false);
    }

    private void restauranteInactivo() {
        when(restauranteRepository.existsByRestauranteIdAndEstado(7L, EstadoRestaurante.INACTIVO))
                .thenReturn(true);
    }

    @Test
    void escrituraCanceladaDa402YNoSigue() throws Exception {
        restauranteActivo();
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(7L))
                .thenReturn(Optional.of(suscripcion(EstadoSuscripcion.CANCELADA)));
        var res = response();
        filter.doFilter(request("POST", "/api/v1/pedidos"), res, chain);
        verify(res).setStatus(402);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void escrituraActivaPasa() throws Exception {
        restauranteActivo();
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(7L))
                .thenReturn(Optional.of(suscripcion(EstadoSuscripcion.ACTIVA)));
        var res = response();
        filter.doFilter(request("POST", "/api/v1/pedidos"), res, chain);
        verify(chain, times(1)).doFilter(any(), any());
        verify(res, never()).setStatus(anyInt());
    }

    @Test
    void lecturaCanceladaPasaSinConsultarDB() throws Exception {
        var res = response();
        filter.doFilter(request("GET", "/api/v1/pedidos"), res, chain);
        verify(chain, times(1)).doFilter(any(), any());
        verifyNoInteractions(suscripcionRepository);
        verifyNoInteractions(restauranteRepository);
    }

    @Test
    void renovarEsHatchDeReactivacion() throws Exception {
        var res = response();
        filter.doFilter(request("POST", "/api/v1/restaurantes/actual/suscripcion/renovar"), res, chain);
        verify(chain, times(1)).doFilter(any(), any());
        verifyNoInteractions(suscripcionRepository);
        verifyNoInteractions(restauranteRepository);
    }

    @Test
    void loginSiemprePasa() throws Exception {
        var res = response();
        filter.doFilter(request("POST", "/api/v1/auth/login"), res, chain);
        verify(chain, times(1)).doFilter(any(), any());
        verifyNoInteractions(suscripcionRepository);
        verifyNoInteractions(restauranteRepository);
    }

    @Test
    void sinTenantPasaPlataforma() throws Exception {
        TenantContext.clear();
        var res = response();
        filter.doFilter(request("POST", "/api/v1/restaurantes"), res, chain);
        verify(chain, times(1)).doFilter(any(), any());
        verifyNoInteractions(suscripcionRepository);
        verifyNoInteractions(restauranteRepository);
    }

    @Test
    void sinSuscripcionPasa() throws Exception {
        restauranteActivo();
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(7L))
                .thenReturn(Optional.empty());
        var res = response();
        filter.doFilter(request("POST", "/api/v1/pedidos"), res, chain);
        verify(chain, times(1)).doFilter(any(), any());
    }

    // ---------------------------------------------------------------------
    // Palanca de restaurante.estado = INACTIVO
    // ---------------------------------------------------------------------

    /** La suspension por restaurante INACTIVO frena la escritura con 402. */
    @Test
    void escrituraConRestauranteInactivoDa402() throws Exception {
        restauranteInactivo();
        var res = response();
        filter.doFilter(request("POST", "/api/v1/pedidos"), res, chain);
        verify(res).setStatus(402);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void escrituraConRestauranteInactivoBloqueaTodosLosMetodosDeEscritura() throws Exception {
        for (var metodo : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
            restauranteInactivo();
            var res = response();
            filter.doFilter(request(metodo, "/api/v1/pedidos"), res, chain);
            verify(res).setStatus(402);
            verify(chain, never()).doFilter(any(), any());
        }
    }

    /**
     * El token limitado tiene que servir para algo: con el restaurante INACTIVO el
     * login sigue emitido el token y las lecturas pasan, para que el frontend pueda
     * mostrar sus datos junto con el aviso de pago.
     */
    @Test
    void lecturaConRestauranteInactivoPasa() throws Exception {
        var res = response();
        filter.doFilter(request("GET", "/api/v1/restaurantes/actual"), res, chain);
        verify(chain, times(1)).doFilter(any(), any());
        verify(res, never()).setStatus(anyInt());
        verifyNoInteractions(suscripcionRepository);
    }

    /** Renovar es la unica escritura que pasa aunque el restaurante este INACTIVO. */
    @Test
    void renovarPasaAunqueElRestauranteEsteInactivo() throws Exception {
        var res = response();
        filter.doFilter(request("POST", "/api/v1/restaurantes/actual/suscripcion/renovar"), res, chain);
        verify(chain, times(1)).doFilter(any(), any());
        verifyNoInteractions(restauranteRepository);
    }

    /** Cortocircuita: si el restaurante ya esta INACTIVO no llega a tocar suscripcion. */
    @Test
    void restauranteInactivoSeChequeaAntesQueLaSuscripcion() throws Exception {
        restauranteInactivo();
        var res = response();
        filter.doFilter(request("POST", "/api/v1/pedidos"), res, chain);
        verify(res).setStatus(402);
        verifyNoInteractions(suscripcionRepository);
    }

    // ---------------------------------------------------------------------
    // Vencimiento por fecha_fin
    // ---------------------------------------------------------------------

    private HttpServletResponse responseConCuerpo(StringWriter cuerpo) throws Exception {
        var res = mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(new PrintWriter(cuerpo));
        return res;
    }

    private SuscripcionJpaEntity suscripcionVencida() {
        var e = suscripcion(EstadoSuscripcion.ACTIVA);
        e.setFechaFin(java.time.LocalDate.now().minusDays(1));
        return e;
    }

    @Test
    void escrituraConSuscripcionVencidaDa402() throws Exception {
        restauranteActivo();
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(7L))
                .thenReturn(Optional.of(suscripcionVencida()));
        var cuerpo = new StringWriter();
        var res = responseConCuerpo(cuerpo);
        filter.doFilter(request("POST", "/api/v1/pedidos"), res, chain);
        verify(res).setStatus(402);
        verify(chain, never()).doFilter(any(), any());
        org.junit.jupiter.api.Assertions.assertTrue(
                cuerpo.toString().contains(java.time.LocalDate.now().minusDays(1).toString()),
                "el 402 de vencimiento tiene que decir desde cuándo: " + cuerpo);
    }

    /**
     * La fecha de fin es el día inclusive: hoy todavía opera. Si mañana se renueva,
     * la nueva arranca desde hoy, así que no hay un día muerto en el medio.
     */
    @Test
    void suscripcionQueVenceHoyTodaviaOpera() throws Exception {
        restauranteActivo();
        var e = suscripcion(EstadoSuscripcion.ACTIVA);
        e.setFechaFin(java.time.LocalDate.now());
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(7L))
                .thenReturn(Optional.of(e));
        var res = response();
        filter.doFilter(request("POST", "/api/v1/pedidos"), res, chain);
        verify(chain, times(1)).doFilter(any(), any());
        verify(res, never()).setStatus(anyInt());
    }

    @Test
    void lecturaConSuscripcionVencidaPasa() throws Exception {
        var res = response();
        filter.doFilter(request("GET", "/api/v1/pedidos"), res, chain);
        verify(chain, times(1)).doFilter(any(), any());
        verify(res, never()).setStatus(anyInt());
    }
}