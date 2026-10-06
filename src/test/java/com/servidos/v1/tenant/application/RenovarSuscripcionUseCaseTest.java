package com.servidos.v1.tenant.application;

import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.tenant.application.suscripcion.RenovarSuscripcionCommand;
import com.servidos.v1.tenant.application.suscripcion.RenovarSuscripcionUseCase;
import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenovarSuscripcionUseCaseTest {

    @Mock SuscripcionJpaRepository suscripcionRepository;
    @Mock PlanJpaRepository planRepository;
    @Mock SuscripcionMapper suscripcionMapper;
    @Mock UsuarioJpaRepository usuarioRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EventPublisher eventPublisher;

    @InjectMocks RenovarSuscripcionUseCase useCase;

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private SuscripcionJpaEntity actual(LocalDate fin) {
        var e = new SuscripcionJpaEntity();
        e.setEstado(EstadoSuscripcion.ACTIVA);
        e.setFechaFin(fin);
        e.setPlanId(3L);
        return e;
    }

    private PlanJpaEntity planActivo(BigDecimal precio) {
        var p = new PlanJpaEntity();
        p.setPlanId(3L);
        p.setNombrePlan("Estándar");
        p.setPrecioPlan(precio);
        p.setEstado(Plan.EstadoPlan.ACTIVO.name());
        return p;
    }

    private void stubsPasswordOk() {
        CurrentUser.setCurrentUser(42L);
        var usuario = new UsuarioJpaEntity();
        usuario.setPasswordHash("hash");
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("secreto", "hash")).thenReturn(true);
    }

    private void stubMapperReal() {
        when(suscripcionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(suscripcionMapper.toEntity(any())).thenCallRealMethod();
        when(suscripcionMapper.toDomain(any())).thenCallRealMethod();
    }

    @Test
    void vigenteExtiendeUnMesExacto() {
        stubsPasswordOk();
        var fin = LocalDate.now().plusDays(5);
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(7L))
                .thenReturn(Optional.of(actual(fin)));
        when(planRepository.findByPlanIdAndEstado(3L, Plan.EstadoPlan.ACTIVO.name()))
                .thenReturn(Optional.of(planActivo(new BigDecimal("34.90"))));
        stubMapperReal();

        var renovada = useCase.ejecutar(new RenovarSuscripcionCommand(7L, "secreto")).suscripcion();

        assertEquals(fin.plusDays(1), renovada.getFecha_inicio());
        assertEquals(fin.plusDays(1).plusMonths(1), renovada.getFecha_fin());
    }

    @Test
    void sinSuscripcionFalla() {
        stubsPasswordOk();
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(7L))
                .thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new RenovarSuscripcionCommand(7L, "secreto")));
    }

    @Test
    void canceladaReactivaConNuevaActiva() {
        stubsPasswordOk();
        var e = actual(LocalDate.now().minusDays(40));
        e.setEstado(EstadoSuscripcion.CANCELADA);
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(7L))
                .thenReturn(Optional.of(e));
        when(planRepository.findByPlanIdAndEstado(3L, Plan.EstadoPlan.ACTIVO.name()))
                .thenReturn(Optional.of(planActivo(new BigDecimal("34.90"))));
        stubMapperReal();

        var reactivada = useCase.ejecutar(new RenovarSuscripcionCommand(7L, "secreto")).suscripcion();

        assertEquals(EstadoSuscripcion.ACTIVA, reactivada.getEstado());
        assertEquals(LocalDate.now(), reactivada.getFecha_inicio());
        assertEquals(LocalDate.now().plusMonths(1), reactivada.getFecha_fin());
    }

    @Test
    void tomaElPrecioDeListaVigenteYNoElMontoAnterior() {
        stubsPasswordOk();
        var anterior = actual(LocalDate.now().minusDays(40));
        anterior.setEstado(EstadoSuscripcion.CANCELADA);
        anterior.setMonto(new BigDecimal("29.90"));
        anterior.setMoneda("PEN");

        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(7L))
                .thenReturn(Optional.of(anterior));
        when(planRepository.findByPlanIdAndEstado(3L, Plan.EstadoPlan.ACTIVO.name()))
                .thenReturn(Optional.of(planActivo(new BigDecimal("39.90"))));
        stubMapperReal();

        var renovada = useCase.ejecutar(new RenovarSuscripcionCommand(7L, "secreto")).suscripcion();

        assertEquals(0, new BigDecimal("39.90").compareTo(renovada.getMonto()));
        assertEquals("PEN", renovada.getMoneda());
    }

    @Test
    void planInactivoImpideRenovar() {
        stubsPasswordOk();
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(7L))
                .thenReturn(Optional.of(actual(LocalDate.now().minusDays(40))));
        when(planRepository.findByPlanIdAndEstado(3L, Plan.EstadoPlan.ACTIVO.name()))
                .thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new RenovarSuscripcionCommand(7L, "secreto")));
        assertEquals("El plan del restaurante no está activo, no se puede renovar", ex.getMessage());
    }

    /**
     * Renovar exige la contraseña del usuario en sesión: no puede ser un clic casual.
     * Sin esto, un restaurante vencido se auto-devolvería el acceso sin haber pagado.
     */
    @Test
    void sinPasswordFalla() {
        var ex = assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new RenovarSuscripcionCommand(7L, null)));
        assertEquals("La contraseña es obligatoria", ex.getMessage());
        verify(suscripcionRepository, never())
                .findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(any());
    }

    @Test
    void passwordIncorrectaNoRenueva() {
        CurrentUser.setCurrentUser(42L);
        var usuario = new UsuarioJpaEntity();
        usuario.setPasswordHash("hash");
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("otra", "hash")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () ->
                useCase.ejecutar(new RenovarSuscripcionCommand(7L, "otra")));
        verify(suscripcionRepository, never()).save(any());
    }
}