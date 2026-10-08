package com.servidos.v1.tenant.application;

import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.tenant.application.suscripcion.CambiarPlanCommand;
import com.servidos.v1.tenant.application.suscripcion.CambiarPlanUseCase;
import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CambiarPlanHardenTest {

    @Mock SuscripcionJpaRepository suscripcionRepository;
    @Mock PlanJpaRepository planRepository;
    @Mock RestauranteJpaRepository restauranteRepository;
    @Mock SuscripcionMapper suscripcionMapper;
    @Mock EventPublisher eventPublisher;
    @Mock UsuarioJpaRepository usuarioRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks CambiarPlanUseCase useCase;

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private void stubsOk() {
        CurrentUser.setCurrentUser(42L);
        var usuario = new UsuarioJpaEntity();
        usuario.setPasswordHash("hash");
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("secreto", "hash")).thenReturn(true);
        when(restauranteRepository.existsById(7L)).thenReturn(true);
        var nuevoPlan = new PlanJpaEntity();
        nuevoPlan.setPlanId(4L);
        nuevoPlan.setNombrePlan("Estándar");
        nuevoPlan.setPrecioPlan(new BigDecimal("34.90"));
        nuevoPlan.setEstado(Plan.EstadoPlan.ACTIVO.name());
        when(planRepository.findByPlanIdAndEstado(4L, Plan.EstadoPlan.ACTIVO.name()))
                .thenReturn(Optional.of(nuevoPlan));
        var actual = new SuscripcionJpaEntity();
        actual.setEstado(EstadoSuscripcion.ACTIVA);
        actual.setPlanId(3L);
        actual.setFechaFin(LocalDate.now().plusDays(5));
        when(suscripcionRepository.findTopByRestauranteIdAndFechaInicioLessThanEqualOrderBySuscripcionIdDesc(eq(7L), any()))
                .thenReturn(Optional.of(actual));
        when(suscripcionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(suscripcionMapper.toEntity(any())).thenCallRealMethod();
        when(suscripcionMapper.toDomain(any())).thenCallRealMethod();
    }

    @Test
    void sinConfirmacionFalla() {
        assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new CambiarPlanCommand(7L, 4L, false, "secreto")));
    }

    @Test
    void passwordIncorrectaNoMuta() {
        CurrentUser.setCurrentUser(42L);
        var usuario = new UsuarioJpaEntity();
        usuario.setPasswordHash("hash");
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("otra", "hash")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () ->
                useCase.ejecutar(new CambiarPlanCommand(7L, 4L, true, "otra")));
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    void confirmadoYPasswordOkCreaNueva() {
        stubsOk();
        var nueva = useCase.ejecutar(new CambiarPlanCommand(7L, 4L, true, "secreto"));
        assertEquals(4L, nueva.suscripcion().getPlan_id());
        assertEquals(LocalDate.now().plusDays(30), nueva.suscripcion().getFecha_fin());
        assertEquals("Estándar", nueva.nombrePlan());
        assertEquals(0, new BigDecimal("34.90").compareTo(nueva.suscripcion().getMonto()));
    }

    /** Un plan retirado no se puede contratar: antes existsById lo aceptaba. */
    @Test
    void planInactivoNoSePuedeContratar() {
        CurrentUser.setCurrentUser(42L);
        var usuario = new UsuarioJpaEntity();
        usuario.setPasswordHash("hash");
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("secreto", "hash")).thenReturn(true);
        when(restauranteRepository.existsById(7L)).thenReturn(true);
        when(planRepository.findByPlanIdAndEstado(4L, Plan.EstadoPlan.ACTIVO.name()))
                .thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new CambiarPlanCommand(7L, 4L, true, "secreto")));
        assertEquals("El plan no existe o no está activo", ex.getMessage());
        verify(suscripcionRepository, never()).save(any());
    }
}
