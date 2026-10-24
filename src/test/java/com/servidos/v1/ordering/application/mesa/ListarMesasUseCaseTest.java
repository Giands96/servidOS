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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarMesasUseCaseTest {

    @Mock
    MesaJpaRepository mesaRepository;

    ListarMesasUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListarMesasUseCase(mesaRepository, new MesaMapper());
    }

    private static MesaJpaEntity entidad(Long id, int numero, EstadoMesa estado) {
        var e = new MesaJpaEntity();
        e.setMesaId(id);
        e.setRestauranteId(10L);
        e.setNumero(numero);
        e.setCapacidad(4);
        e.setEstado(estado);
        return e;
    }

    @Test
    void sinFiltroListaTodasLasMesasActivasDelRestaurante() {
        when(mesaRepository.findByRestauranteIdAndActivaTrueOrderByNumeroAsc(10L))
                .thenReturn(List.of(entidad(1L, 1, EstadoMesa.LIBRE), entidad(2L, 2, EstadoMesa.OCUPADA)));

        var mesas = useCase.listar(10L, null);

        assertEquals(2, mesas.size());
        assertEquals(EstadoMesa.OCUPADA, mesas.get(1).getEstado());
    }

    @Test
    void conEstadoFiltraPorEseEstado() {
        when(mesaRepository.findByRestauranteIdAndActivaTrueAndEstadoOrderByNumeroAsc(10L, EstadoMesa.LIBRE))
                .thenReturn(List.of(entidad(1L, 1, EstadoMesa.LIBRE)));

        var mesas = useCase.listar(10L, EstadoMesa.LIBRE);

        assertEquals(1, mesas.size());
        assertEquals(1L, mesas.get(0).getMesa_id());
    }

    @Test
    void sinRestauranteDa400() {
        assertThrows(BusinessException.class, () -> useCase.listar(null, null));
    }

    @Test
    void listarInactivasDevuelveSoloLasDadasDeBajaDelRestaurante() {
        var baja = entidad(4L, 4, EstadoMesa.LIBRE);
        baja.setActiva(false);
        when(mesaRepository.findByRestauranteIdAndActivaFalseOrderByNumeroAsc(10L)).thenReturn(List.of(baja));

        var mesas = useCase.listarInactivas(10L);

        assertEquals(1, mesas.size());
        assertEquals(4L, mesas.get(0).getMesa_id());
        assertFalse(mesas.get(0).isActiva());
    }

    @Test
    void listarInactivasSinRestauranteDa400() {
        assertThrows(BusinessException.class, () -> useCase.listarInactivas(null));
    }
}
