package com.servidos.v1.tenant.application;

import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.application.suscripcion.RenovarSuscripcionCommand;
import com.servidos.v1.tenant.application.suscripcion.RenovarSuscripcionUseCase;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.domain.event.RestauranteEstadoCambiadoEvent;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock RestauranteJpaRepository restauranteRepository;
    @Mock EventPublisher eventPublisher;

    RenovarSuscripcionUseCase useCase;
    RestauranteJpaEntity restaurante;

    @BeforeEach
    void setUp() {
        useCase = new RenovarSuscripcionUseCase(suscripcionRepository, planRepository, restauranteRepository,
                new SuscripcionMapper(), eventPublisher);
        restaurante = new RestauranteJpaEntity();
        restaurante.setRestauranteId(7L);
        restaurante.setEstado(EstadoRestaurante.ACTIVO);
    }

    private void dadoRestaurante() {
        when(restauranteRepository.findById(7L)).thenReturn(Optional.of(restaurante));
    }

    private void dadaUltima(EstadoSuscripcion estado, LocalDate fin) {
        var e = new SuscripcionJpaEntity();
        e.setEstado(estado);
        e.setFechaFin(fin);
        e.setPlanId(3L);
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(7L))
                .thenReturn(Optional.of(e));
    }

    private void dadoPlanActivo(Long planId, String precio) {
        var p = new PlanJpaEntity();
        p.setPlanId(planId);
        p.setNombrePlan("Estándar");
        p.setPrecioPlan(new BigDecimal(precio));
        p.setEstado(Plan.EstadoPlan.ACTIVO.name());
        when(planRepository.findByPlanIdAndEstado(planId, Plan.EstadoPlan.ACTIVO.name())).thenReturn(Optional.of(p));
    }

    private void guardarDevuelveLoMismo() {
        when(suscripcionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void vigenteQuedaProgramadaDesdeElDiaSiguienteASuFin() {
        dadoRestaurante();
        var fin = LocalDate.now().plusDays(5);
        dadaUltima(EstadoSuscripcion.ACTIVA, fin);
        dadoPlanActivo(3L, "34.90");
        guardarDevuelveLoMismo();

        var renovada = useCase.ejecutar(new RenovarSuscripcionCommand(7L, null)).suscripcion();

        assertEquals(fin.plusDays(1), renovada.getFecha_inicio());
        assertEquals(fin.plusDays(1).plusMonths(1), renovada.getFecha_fin());
        assertEquals(EstadoSuscripcion.ACTIVA, renovada.getEstado());
    }

    @Test
    void vencidaOCanceladaArrancaHoy() {
        dadoRestaurante();
        dadaUltima(EstadoSuscripcion.CANCELADA, LocalDate.now().minusDays(40));
        dadoPlanActivo(3L, "34.90");
        guardarDevuelveLoMismo();

        var renovada = useCase.ejecutar(new RenovarSuscripcionCommand(7L, null)).suscripcion();

        assertEquals(LocalDate.now(), renovada.getFecha_inicio());
        assertEquals(LocalDate.now().plusMonths(1), renovada.getFecha_fin());
    }

    @Test
    void tomaElPrecioDeListaVigenteYNoElMontoAnterior() {
        dadoRestaurante();
        dadaUltima(EstadoSuscripcion.ACTIVA, LocalDate.now().minusDays(40));
        dadoPlanActivo(3L, "39.90");
        guardarDevuelveLoMismo();

        var renovada = useCase.ejecutar(new RenovarSuscripcionCommand(7L, null)).suscripcion();

        assertEquals(0, new BigDecimal("39.90").compareTo(renovada.getMonto()));
        assertEquals("PEN", renovada.getMoneda());
    }

    @Test
    void puedeRenovarEnOtroPlan() {
        dadoRestaurante();
        dadaUltima(EstadoSuscripcion.ACTIVA, LocalDate.now().minusDays(1));
        dadoPlanActivo(9L, "59.90");
        guardarDevuelveLoMismo();

        var renovada = useCase.ejecutar(new RenovarSuscripcionCommand(7L, 9L)).suscripcion();

        assertEquals(9L, renovada.getPlan_id());
    }

    @Test
    void sinSuscripcionPreviaCreaLaPrimeraConElPlanIndicado() {
        dadoRestaurante();
        dadoPlanActivo(3L, "34.90");
        guardarDevuelveLoMismo();

        var primera = useCase.ejecutar(new RenovarSuscripcionCommand(7L, 3L)).suscripcion();

        assertEquals(LocalDate.now(), primera.getFecha_inicio());
        assertEquals(3L, primera.getPlan_id());
    }

    @Test
    void sinSuscripcionPreviaNiPlanFalla() {
        dadoRestaurante();

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new RenovarSuscripcionCommand(7L, null)));
        assertEquals("El plan es obligatorio para la primera suscripción", ex.getMessage());
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    void restauranteInactivoQuedaActivo() {
        restaurante.setEstado(EstadoRestaurante.INACTIVO);
        dadoRestaurante();
        dadaUltima(EstadoSuscripcion.ACTIVA, LocalDate.now().minusDays(10));
        dadoPlanActivo(3L, "34.90");
        guardarDevuelveLoMismo();

        useCase.ejecutar(new RenovarSuscripcionCommand(7L, null));

        assertEquals(EstadoRestaurante.ACTIVO, restaurante.getEstado());
        verify(restauranteRepository).save(restaurante);
        verify(eventPublisher).publish(any(RestauranteEstadoCambiadoEvent.class));
    }

    @Test
    void restauranteActivoNoSeTocaNiPublicaEvento() {
        dadoRestaurante();
        dadaUltima(EstadoSuscripcion.ACTIVA, LocalDate.now().minusDays(10));
        dadoPlanActivo(3L, "34.90");
        guardarDevuelveLoMismo();

        useCase.ejecutar(new RenovarSuscripcionCommand(7L, null));

        verify(restauranteRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void planInactivoImpideRenovar() {
        dadoRestaurante();
        dadaUltima(EstadoSuscripcion.ACTIVA, LocalDate.now().minusDays(40));
        when(planRepository.findByPlanIdAndEstado(3L, Plan.EstadoPlan.ACTIVO.name())).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new RenovarSuscripcionCommand(7L, null)));
        assertEquals("El plan no existe o no está activo", ex.getMessage());
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    void restauranteInexistenteFalla() {
        assertThrows(BusinessException.class, () -> useCase.ejecutar(new RenovarSuscripcionCommand(7L, 3L)));
    }
}
