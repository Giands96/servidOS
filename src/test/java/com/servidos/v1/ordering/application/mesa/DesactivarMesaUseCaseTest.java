package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.MesaMapper;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesactivarMesaUseCaseTest {

    @Mock
    MesaJpaRepository mesaRepository;

    @Mock
    PedidoJpaRepository pedidoRepository;

    DesactivarMesaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DesactivarMesaUseCase(mesaRepository, pedidoRepository, new MesaMapper());
    }

    private static MesaJpaEntity entidad(EstadoMesa estado, boolean activa) {
        var e = new MesaJpaEntity();
        e.setMesaId(1L);
        e.setRestauranteId(10L);
        e.setNumero(5);
        e.setCapacidad(4);
        e.setEstado(estado);
        e.setActiva(activa);
        return e;
    }

    private void dadaMesa(MesaJpaEntity e) {
        when(mesaRepository.bloquearPorIdYRestaurante(1L, 10L)).thenReturn(Optional.of(e));
    }

    @Test
    void daDeBajaLaMesaLibreSinPedidosAbiertos() {
        var e = entidad(EstadoMesa.LIBRE, true);
        dadaMesa(e);
        when(pedidoRepository.contarAbiertosEnMesa(1L, 10L)).thenReturn(0L);
        when(mesaRepository.saveAndFlush(any(MesaJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var mesa = useCase.ejecutar(1L, 10L);

        assertFalse(mesa.isActiva());
        assertFalse(e.isActiva());
    }

    @Test
    void mesaOcupadaDa400YNoGuarda() {
        var e = entidad(EstadoMesa.OCUPADA, true);
        dadaMesa(e);

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, 10L));

        assertEquals("No se puede dar de baja una mesa ocupada", ex.getMessage());
        assertTrue(e.isActiva());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void conPedidosAbiertosDa400YNoGuarda() {
        var e = entidad(EstadoMesa.LIBRE, true);
        dadaMesa(e);
        when(pedidoRepository.contarAbiertosEnMesa(1L, 10L)).thenReturn(1L);

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, 10L));

        assertEquals("La mesa tiene pedidos abiertos", ex.getMessage());
        assertTrue(e.isActiva());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void mesaYaInactivaDa400() {
        dadaMesa(entidad(EstadoMesa.LIBRE, false));

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, 10L));

        assertEquals("La mesa ya está inactiva", ex.getMessage());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void mesaDeOtroRestauranteDa400() {
        when(mesaRepository.bloquearPorIdYRestaurante(1L, 99L)).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, 99L));

        assertEquals("Mesa no encontrada", ex.getMessage());
    }

    @Test
    void sinRestauranteOSinMesaDa400() {
        assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, null));
        assertThrows(BusinessException.class, () -> useCase.ejecutar(null, 10L));
    }
}
