package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.MesaMapper;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActualizarMesaUseCaseTest {

    @Mock
    MesaJpaRepository mesaRepository;

    ActualizarMesaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ActualizarMesaUseCase(mesaRepository, new MesaMapper());
    }

    private static MesaJpaEntity entidad(EstadoMesa estado, Integer comensales) {
        var e = new MesaJpaEntity();
        e.setMesaId(1L);
        e.setRestauranteId(10L);
        e.setNumero(5);
        e.setCapacidad(6);
        e.setComensales(comensales);
        e.setEstado(estado);
        return e;
    }

    private void dadaMesa(MesaJpaEntity e) {
        when(mesaRepository.bloquearPorIdYRestaurante(1L, 10L)).thenReturn(Optional.of(e));
    }

    @Test
    void actualizaNumeroYCapacidad() {
        var e = entidad(EstadoMesa.LIBRE, null);
        dadaMesa(e);
        when(mesaRepository.saveAndFlush(any(MesaJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var mesa = useCase.ejecutar(new ActualizarMesaCommand(1L, 8, 10), 10L);

        assertEquals(8, mesa.getNumero());
        assertEquals(10, mesa.getCapacidad());
        assertEquals(8, e.getNumero());
        assertEquals(10, e.getCapacidad());
    }

    @Test
    void mesaDeOtroRestauranteDa400() {
        when(mesaRepository.bloquearPorIdYRestaurante(1L, 99L)).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new ActualizarMesaCommand(1L, 8, 10), 99L));

        assertEquals("Mesa no encontrada", ex.getMessage());
    }

    @Test
    void numeroDeOtraMesaDelRestauranteDa400YNoGuarda() {
        dadaMesa(entidad(EstadoMesa.LIBRE, null));
        when(mesaRepository.existsByRestauranteIdAndNumeroAndMesaIdNot(10L, 8, 1L)).thenReturn(true);

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new ActualizarMesaCommand(1L, 8, 6), 10L));

        assertEquals("La mesa 8 ya existe para este restaurante", ex.getMessage());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicadoQueGanaLaCarreraEnLaBaseTambienDa400() {
        dadaMesa(entidad(EstadoMesa.LIBRE, null));
        when(mesaRepository.saveAndFlush(any(MesaJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq mesa"));

        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new ActualizarMesaCommand(1L, 8, 6), 10L));
    }

    @Test
    void capacidadMenorALosComensalesActualesDa400() {
        dadaMesa(entidad(EstadoMesa.OCUPADA, 5));

        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new ActualizarMesaCommand(1L, 5, 4), 10L));

        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void sinRestauranteDa400() {
        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new ActualizarMesaCommand(1L, 8, 10), null));
    }
}
