package com.servidos.v1.tenant.infrastructure;

import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contra Postgres real (perfil test): las tres consultas de "suscripción actual" tienen
 * que coincidir y no tomar una renovación programada como actual. Cada test hace
 * rollback, así que no deja datos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SuscripcionActualRepositoryTest {

    @Autowired SuscripcionJpaRepository suscripcionRepository;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired PlanJpaRepository planRepository;

    final LocalDate hoy = LocalDate.now();
    Long restauranteId;
    Long planId;

    @BeforeEach
    void setUp() {
        var r = new RestauranteJpaEntity();
        r.setSlug("it-actual-" + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        restauranteId = restauranteRepository.save(r).getRestauranteId();
        planId = planRepository.findByEstadoOrderByPrecioPlanAsc("ACTIVO").getFirst().getPlanId();
    }

    private SuscripcionJpaEntity guardar(EstadoSuscripcion estado, LocalDate inicio, LocalDate fin) {
        var s = new SuscripcionJpaEntity();
        s.setRestauranteId(restauranteId);
        s.setPlanId(planId);
        s.setMonto(new BigDecimal("34.90"));
        s.setMoneda("PEN");
        s.setEstado(estado);
        s.setFechaInicio(inicio);
        s.setFechaFin(fin);
        return suscripcionRepository.saveAndFlush(s);
    }

    @Test
    void laProgramadaNoReemplazaALaVigente() {
        var vigente = guardar(EstadoSuscripcion.ACTIVA, hoy.minusDays(20), hoy.plusDays(10));
        var programada = guardar(EstadoSuscripcion.ACTIVA, hoy.plusDays(11), hoy.plusDays(41));

        var actual = suscripcionRepository
                .findTopByRestauranteIdAndFechaInicioLessThanEqualOrderBySuscripcionIdDesc(restauranteId, hoy);
        assertEquals(vigente.getSuscripcionId(), actual.orElseThrow().getSuscripcionId());

        var conPlan = suscripcionRepository.listarActualConNombrePlan(restauranteId, hoy);
        assertEquals(1, conPlan.size());
        assertEquals(vigente.getSuscripcionId(), conPlan.getFirst().suscripcion().getSuscripcion_id());

        var fila = restauranteRepository.listarConSuscripcionActual(hoy, PageRequest.of(0, 500)).stream()
                .filter(f -> f.restauranteId().equals(restauranteId)).findFirst().orElseThrow();
        assertEquals(vigente.getSuscripcionId(), fila.suscripcionId());

        // Y la "última" (base para encadenar) sí es la programada.
        assertEquals(programada.getSuscripcionId(), suscripcionRepository
                .findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(restauranteId).orElseThrow().getSuscripcionId());
    }

    @Test
    void cuandoLaProgramadaEmpiezaPasaASerLaActual() {
        guardar(EstadoSuscripcion.ACTIVA, hoy.minusDays(40), hoy.minusDays(10));
        var nueva = guardar(EstadoSuscripcion.ACTIVA, hoy.minusDays(9), hoy.plusDays(20));

        assertEquals(nueva.getSuscripcionId(), suscripcionRepository
                .findTopByRestauranteIdAndFechaInicioLessThanEqualOrderBySuscripcionIdDesc(restauranteId, hoy)
                .orElseThrow().getSuscripcionId());
    }

    @Test
    void soloProgramadaEquivaleASinSuscripcionActual() {
        guardar(EstadoSuscripcion.ACTIVA, hoy.plusDays(1), hoy.plusDays(31));

        assertTrue(suscripcionRepository
                .findTopByRestauranteIdAndFechaInicioLessThanEqualOrderBySuscripcionIdDesc(restauranteId, hoy).isEmpty());
        assertTrue(suscripcionRepository.listarActualConNombrePlan(restauranteId, hoy).isEmpty());
    }

    @Test
    void programadasActivasSeEncuentranParaCancelarlas() {
        guardar(EstadoSuscripcion.ACTIVA, hoy.minusDays(5), hoy.plusDays(25));
        var programada = guardar(EstadoSuscripcion.ACTIVA, hoy.plusDays(26), hoy.plusDays(56));

        var programadas = suscripcionRepository
                .findByRestauranteIdAndEstadoAndFechaInicioAfter(restauranteId, EstadoSuscripcion.ACTIVA, hoy);
        assertEquals(1, programadas.size());
        assertEquals(programada.getSuscripcionId(), programadas.getFirst().getSuscripcionId());
    }
}
