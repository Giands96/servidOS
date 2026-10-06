package com.servidos.v1.identity.api;

import com.servidos.v1.identity.api.dto.MeResponse;
import com.servidos.v1.identity.application.auth.LoginUseCase;
import com.servidos.v1.identity.application.auth.RefreshTokenService;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.security.JwtService;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.shared.security.TenantContext;
import com.servidos.v1.tenant.application.restaurante.ObtenerRestauranteUseCase;
import com.servidos.v1.tenant.application.suscripcion.SuscripcionConPlan;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.domain.Restaurante;
import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerMeTest {

    @Mock UsuarioJpaRepository usuarioRepository;
    @Mock ObtenerRestauranteUseCase obtenerRestauranteUseCase;
    @Mock LoginUseCase loginUseCase;
    @Mock RefreshTokenService refreshTokenService;
    @Mock JwtService jwtService;

    AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(loginUseCase, refreshTokenService, jwtService,
                usuarioRepository, obtenerRestauranteUseCase);
        CurrentUser.setCurrentUser(5L);
        CurrentUser.setRole("ADMINISTRADOR");
        TenantContext.setRestauranteId(1L);
    }

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
        TenantContext.clear();
    }

    private void stubUsuario() {
        var usuario = new UsuarioJpaEntity();
        usuario.setUsuarioId(5L);
        usuario.setEmail("admin@demo.pe");
        usuario.setNombre("Demo");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
    }

    private void stubRestaurante(EstadoRestaurante estado) {
        when(obtenerRestauranteUseCase.obtener(1L)).thenReturn(
                Restaurante.builder().restaurante_id(1L).slug("demo").nombre("Demo")
                        .estado(estado).build());
    }

    private void stubSuscripcion(LocalDate fechaFin) {
        var s = Suscripcion.builder().suscripcion_id(9L).restaurante_id(1L).plan_id(1L)
                .monto(new BigDecimal("34.90")).moneda("PEN")
                .estado(EstadoSuscripcion.ACTIVA)
                .fecha_inicio(fechaFin.minusDays(30))
                .fecha_fin(fechaFin).build();
        when(obtenerRestauranteUseCase.obtenerSuscripcion(1L))
                .thenReturn(new SuscripcionConPlan(s, "Estándar"));
    }

    @Test
    void sesionTenantDevuelveIdentidadYResumen() {
        stubUsuario();
        stubRestaurante(EstadoRestaurante.ACTIVO);
        stubSuscripcion(LocalDate.now().plusDays(29));

        var http = controller.me();
        MeResponse resp = http.getBody();

        assertEquals(HttpStatus.OK, http.getStatusCode());
        assertEquals("Demo", resp.nombre());
        assertEquals(EstadoRestaurante.ACTIVO, resp.restauranteEstado());
        assertEquals("Estándar", resp.suscripcion().nombrePlan());
        assertEquals(0, new BigDecimal("34.90").compareTo(resp.suscripcion().monto()));
        assertEquals("PEN", resp.suscripcion().moneda());
        assertEquals(29, resp.suscripcion().diasRestantes());
        assertFalse(resp.suscripcion().vencida());
    }

    @Test
    void suscripcionVencidaMarcaVencida() {
        stubUsuario();
        stubRestaurante(EstadoRestaurante.ACTIVO);
        stubSuscripcion(LocalDate.now().minusDays(1));

        MeResponse resp = controller.me().getBody();

        assertEquals(-1, resp.suscripcion().diasRestantes());
        assertTrue(resp.suscripcion().vencida());
    }

    @Test
    void sinSuscripcionDevuelveNullPeroConEstado() {
        stubUsuario();
        stubRestaurante(EstadoRestaurante.INACTIVO);
        when(obtenerRestauranteUseCase.obtenerSuscripcion(1L))
                .thenThrow(new BusinessException("No se encontró suscripción para el restaurante"));

        var http = controller.me();
        MeResponse resp = http.getBody();

        assertEquals(HttpStatus.OK, http.getStatusCode());
        assertNull(resp.suscripcion());
        assertEquals(EstadoRestaurante.INACTIVO, resp.restauranteEstado());
    }

    @Test
    void sesionPlataformaNoTocaTenant() {
        stubUsuario();
        TenantContext.clear();

        var http = controller.me();
        MeResponse resp = http.getBody();

        assertEquals(HttpStatus.OK, http.getStatusCode());
        assertNull(resp.restauranteId());
        assertNull(resp.restauranteEstado());
        assertNull(resp.suscripcion());
        verifyNoInteractions(obtenerRestauranteUseCase);
    }

    @Test
    void sinSesionDa401() {
        CurrentUser.clear();

        assertThrows(UnauthorizedException.class, () -> controller.me());
        verifyNoInteractions(usuarioRepository);
        verifyNoInteractions(obtenerRestauranteUseCase);
    }
}