package com.servidos.v1.tenant.application;

import com.servidos.v1.identity.application.usuario.RegistrarAdministradorRestauranteCommand;
import com.servidos.v1.identity.application.usuario.RegistrarAdministradorRestauranteUseCase;
import com.servidos.v1.identity.domain.Usuario;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ConflictException;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteCommand;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteUseCase;
import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.domain.event.RestauranteCreadoEvent;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.RestauranteMapper;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrearRestauranteDemoTest {

    @Mock RestauranteJpaRepository restauranteRepository;
    @Mock SuscripcionJpaRepository suscripcionRepository;
    @Mock PlanJpaRepository planRepository;
    @Mock RestauranteMapper restauranteMapper;
    @Mock SuscripcionMapper suscripcionMapper;
    @Mock EventPublisher eventPublisher;
    @Mock RegistrarAdministradorRestauranteUseCase registrarAdministrador;

    @InjectMocks CrearRestauranteUseCase useCase;

    private static RegistrarAdministradorRestauranteCommand admin() {
        return new RegistrarAdministradorRestauranteCommand("Ana", "Paz", "ana@demo.pe", "secreta123");
    }

    private static CrearRestauranteCommand cmd(Boolean demo, Integer demoDias) {
        return new CrearRestauranteCommand("demo", "Demo", null, 9L, demo, demoDias, admin());
    }

    private void baseStubs() {
        when(restauranteRepository.existsBySlug("demo")).thenReturn(false);
        var plan = new PlanJpaEntity();
        plan.setPlanId(9L);
        plan.setNombrePlan("Estándar");
        plan.setPrecioPlan(new BigDecimal("34.90"));
        plan.setEstado(Plan.EstadoPlan.ACTIVO.name());
        when(planRepository.findByPlanIdAndEstado(9L, Plan.EstadoPlan.ACTIVO.name()))
                .thenReturn(java.util.Optional.of(plan));
        var savedR = new RestauranteJpaEntity();
        savedR.setRestauranteId(5L);
        savedR.setSlug("demo");
        when(restauranteRepository.save(any())).thenReturn(savedR);
        when(restauranteMapper.toEntity(any())).thenCallRealMethod();
        when(restauranteMapper.toDomain(any())).thenCallRealMethod();
        when(suscripcionMapper.toEntity(any())).thenCallRealMethod();
        when(suscripcionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(registrarAdministrador.ejecutar(any(), any())).thenReturn(
                Usuario.builder().usuario_id(77L).nombre("Ana").apellido("Paz").email("ana@demo.pe").build());
    }

    @Test
    void demoUsaDiasVariables() {
        baseStubs();
        useCase.ejecutar(cmd(true, 10));
        var captor = ArgumentCaptor.forClass(SuscripcionJpaEntity.class);
        org.mockito.Mockito.verify(suscripcionRepository).save(captor.capture());
        assertEquals(LocalDate.now().plusDays(10), captor.getValue().getFechaFin());
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getMonto()), "una demo no se cobra");
    }

    @Test
    void sinDemoUsa30Dias() {
        baseStubs();
        useCase.ejecutar(cmd(false, null));
        var captor = ArgumentCaptor.forClass(SuscripcionJpaEntity.class);
        org.mockito.Mockito.verify(suscripcionRepository).save(captor.capture());
        assertEquals(LocalDate.now().plusDays(30), captor.getValue().getFechaFin());
        assertEquals(0, new BigDecimal("34.90").compareTo(captor.getValue().getMonto()));
    }

    @Test
    void demoSinDiasFalla() {
        baseStubs();
        assertThrows(BusinessException.class, () -> useCase.ejecutar(cmd(true, null)));
    }

    @Test
    void demoDiasFueraDeRangoFalla() {
        baseStubs();
        assertThrows(BusinessException.class, () -> useCase.ejecutar(cmd(true, 45)));
    }

    @Test
    void registraAlAdministradorConElIdDelRestauranteNuevoYLoDevuelve() {
        baseStubs();

        var creado = useCase.ejecutar(cmd(false, null));

        verify(registrarAdministrador).ejecutar(eq(admin()), eq(5L));
        assertEquals(5L, creado.restaurante().getRestaurante_id());
        assertEquals("demo", creado.restaurante().getSlug());
        assertEquals(77L, creado.administrador().getUsuario_id());
    }

    @Test
    void elEventoSePublicaSoloDespuesDeCrearAlAdministrador() {
        baseStubs();

        useCase.ejecutar(cmd(false, null));

        InOrder orden = inOrder(restauranteRepository, suscripcionRepository, registrarAdministrador, eventPublisher);
        orden.verify(registrarAdministrador).validarEmailDisponible("ana@demo.pe");
        orden.verify(restauranteRepository).save(any());
        orden.verify(suscripcionRepository).save(any());
        orden.verify(registrarAdministrador).ejecutar(any(), eq(5L));
        orden.verify(eventPublisher).publish(any(RestauranteCreadoEvent.class));
    }

    @Test
    void emailDuplicadoEsConflictoAntesDeGuardarElRestaurante() {
        baseStubs();
        doThrow(new ConflictException("El email ya existe"))
                .when(registrarAdministrador).validarEmailDisponible("ana@demo.pe");

        var ex = assertThrows(ConflictException.class, () -> useCase.ejecutar(cmd(false, null)));

        assertEquals("El email ya existe", ex.getMessage());
        verify(restauranteRepository, never()).save(any());
        verify(suscripcionRepository, never()).save(any());
        verify(registrarAdministrador, never()).ejecutar(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void slugDuplicadoFallaSinTocarAlAdministrador() {
        baseStubs();
        when(restauranteRepository.existsBySlug("demo")).thenReturn(true);

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(cmd(false, null)));

        assertEquals("El slug ya existe", ex.getMessage());
        verifyNoInteractions(registrarAdministrador);
        verify(restauranteRepository, never()).save(any());
    }

    @Test
    void sinAdministradorFallaAntesDeGuardarNada() {
        baseStubs();

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(
                new CrearRestauranteCommand("demo", "Demo", null, 9L, false, null, null)));

        assertEquals("El administrador es obligatorio", ex.getMessage());
        verify(restauranteRepository, never()).save(any());
        verifyNoInteractions(registrarAdministrador);
    }

    @Test
    void siFallaElAdministradorNoSePublicaElEvento() {
        baseStubs();
        when(registrarAdministrador.ejecutar(any(), any())).thenThrow(new ConflictException("El email ya existe"));

        assertThrows(ConflictException.class, () -> useCase.ejecutar(cmd(false, null)));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void elComandoNoExponeElPasswordEnToString() {
        assertFalse(cmd(false, null).toString().contains("secreta123"));
    }
}
