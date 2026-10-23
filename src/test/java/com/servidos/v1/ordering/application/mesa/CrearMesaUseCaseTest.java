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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrearMesaUseCaseTest {

    @Mock
    MesaJpaRepository mesaRepository;

    CrearMesaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CrearMesaUseCase(mesaRepository, new MesaMapper());
    }

    private void dadoMesaGuardada() {
        when(mesaRepository.saveAndFlush(any(MesaJpaEntity.class))).thenAnswer(inv -> {
            MesaJpaEntity e = inv.getArgument(0);
            e.setMesaId(1L);
            return e;
        });
    }

    @Test
    void creaMesaLibreConSuCapacidad() {
        dadoMesaGuardada();

        var mesa = useCase.ejecutar(new CrearMesaCommand(5, 4), 10L);

        assertEquals(1L, mesa.getMesa_id());
        assertEquals(10L, mesa.getRestaurante_id());
        assertEquals(5, mesa.getNumero());
        assertEquals(4, mesa.getCapacidad());
        assertEquals(EstadoMesa.LIBRE, mesa.getEstado());
        assertNull(mesa.getComensales());
    }

    @Test
    void numeroRepetidoEnElRestauranteDa400YNoGuarda() {
        when(mesaRepository.existsByRestauranteIdAndNumero(10L, 5)).thenReturn(true);

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new CrearMesaCommand(5, 4), 10L));

        assertEquals("La mesa 5 ya existe para este restaurante", ex.getMessage());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicadoQueGanaLaCarreraEnLaBaseTambienDa400() {
        when(mesaRepository.saveAndFlush(any(MesaJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq mesa"));

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new CrearMesaCommand(5, 4), 10L));

        assertEquals("La mesa 5 ya existe para este restaurante", ex.getMessage());
    }

    @Test
    void capacidadCeroDa400YNoGuarda() {
        assertThrows(BusinessException.class, () -> useCase.ejecutar(new CrearMesaCommand(5, 0), 10L));

        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void sinRestauranteDa400() {
        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new CrearMesaCommand(5, 4), null));

        assertEquals("Restaurante no identificado", ex.getMessage());
    }
}
