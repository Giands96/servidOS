package com.servidos.v1.ordering;

import com.servidos.v1.ordering.application.mesa.ActualizarMesaCommand;
import com.servidos.v1.ordering.application.mesa.ActualizarMesaUseCase;
import com.servidos.v1.ordering.application.mesa.CrearMesaCommand;
import com.servidos.v1.ordering.application.mesa.CrearMesaUseCase;
import com.servidos.v1.ordering.application.mesa.ListarMesasUseCase;
import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Crear, listar y actualizar mesas contra Postgres real: valida la migración V14
 * (capacidad, comensales, CHECK de estado) y el mapeo JPA (ddl-auto: validate).
 * Rollback por test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MesaPersistenciaIntegracionTest {

    @Autowired CrearMesaUseCase crearMesa;
    @Autowired ListarMesasUseCase listarMesas;
    @Autowired ActualizarMesaUseCase actualizarMesa;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired JdbcTemplate jdbc;

    Long restauranteId;
    Long otroRestauranteId;

    @BeforeEach
    void setUp() {
        restauranteId = nuevoRestaurante("it-mesa-a-");
        otroRestauranteId = nuevoRestaurante("it-mesa-b-");
    }

    private Long nuevoRestaurante(String prefijo) {
        var r = new RestauranteJpaEntity();
        r.setSlug(prefijo + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        return restauranteRepository.save(r).getRestauranteId();
    }

    @Test
    void creaYListaSoloLasMesasDelRestauranteOrdenadasPorNumero() {
        crearMesa.ejecutar(new CrearMesaCommand(2, 4), restauranteId);
        crearMesa.ejecutar(new CrearMesaCommand(1, 6), restauranteId);
        crearMesa.ejecutar(new CrearMesaCommand(1, 2), otroRestauranteId);

        var mesas = listarMesas.listar(restauranteId, null);

        assertEquals(List.of(1, 2), mesas.stream().map(m -> m.getNumero()).toList());
        assertEquals(6, mesas.get(0).getCapacidad());
        assertEquals(EstadoMesa.LIBRE, mesas.get(0).getEstado());
        assertNotNull(mesas.get(0).getMesa_id());
    }

    @Test
    void filtraPorEstado() {
        crearMesa.ejecutar(new CrearMesaCommand(1, 4), restauranteId);

        assertEquals(1, listarMesas.listar(restauranteId, EstadoMesa.LIBRE).size());
        assertEquals(0, listarMesas.listar(restauranteId, EstadoMesa.OCUPADA).size());
    }

    @Test
    void numeroRepetidoEnElMismoRestauranteSeRechazaPeroEnOtroSePermite() {
        crearMesa.ejecutar(new CrearMesaCommand(1, 4), restauranteId);

        assertThrows(BusinessException.class,
                () -> crearMesa.ejecutar(new CrearMesaCommand(1, 4), restauranteId));
        crearMesa.ejecutar(new CrearMesaCommand(1, 4), otroRestauranteId);
    }

    @Test
    void actualizaNumeroYCapacidadYNoVeMesasAjenas() {
        var mesa = crearMesa.ejecutar(new CrearMesaCommand(1, 4), restauranteId);

        var actualizada = actualizarMesa.ejecutar(
                new ActualizarMesaCommand(mesa.getMesa_id(), 9, 8), restauranteId);

        assertEquals(9, actualizada.getNumero());
        assertEquals(8, actualizada.getCapacidad());
        assertThrows(BusinessException.class, () -> actualizarMesa.ejecutar(
                new ActualizarMesaCommand(mesa.getMesa_id(), 9, 8), otroRestauranteId));
    }

    @Test
    void laColumnaActivaVieneEnTruePorDefecto() {
        jdbc.update("INSERT INTO mesa (restaurante_id, numero, capacidad) VALUES (?, 1, 4)", restauranteId);

        var activa = jdbc.queryForObject(
                "SELECT activa FROM mesa WHERE restaurante_id = ? AND numero = 1", Boolean.class, restauranteId);

        assertEquals(true, activa);
    }

    @Test
    void variasMesasInactivasPuedenCompartirNumeroConUnaActiva() {
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO mesa (restaurante_id, numero, capacidad, activa) VALUES (?, 3, 4, FALSE)",
                    restauranteId);
        }
        jdbc.update("INSERT INTO mesa (restaurante_id, numero, capacidad, activa) VALUES (?, 3, 4, TRUE)",
                restauranteId);

        var total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mesa WHERE restaurante_id = ? AND numero = 3", Integer.class, restauranteId);

        assertEquals(3, total);
    }

    @Test
    void laBaseRechazaDosMesasActivasConElMismoNumero() {
        jdbc.update("INSERT INTO mesa (restaurante_id, numero, capacidad) VALUES (?, 3, 4)", restauranteId);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO mesa (restaurante_id, numero, capacidad) VALUES (?, 3, 4)", restauranteId));
    }

    @Test
    void laBaseRechazaCapacidadCero() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO mesa (restaurante_id, numero, capacidad) VALUES (?, 1, 0)", restauranteId));
    }

    @Test
    void laBaseRechazaEstadoFueraDeLibreOcupada() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO mesa (restaurante_id, numero, capacidad, estado) VALUES (?, 1, 4, 'RESERVADA')",
                restauranteId));
    }
}
