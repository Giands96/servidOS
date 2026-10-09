package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.application.PedidoPagoPort;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.PedidoMapper;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ListarPedidosUseCaseTest {

    @Mock PedidoJpaRepository pedidoRepository;
    @Mock PedidoMapper pedidoMapper;
    @Mock PedidoPagoPort pagoPort;

    @InjectMocks ListarPedidosUseCase useCase;

    @Test
    void exigeRestaurante() {
        var ex = assertThrows(BusinessException.class, () -> useCase.listar(null, null, null, false, 0, 20));
        assertEquals("Restaurante no identificado", ex.getMessage());
        verifyNoInteractions(pedidoRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void tamanoFueraDeRangoFalla(int tamano) {
        assertThrows(BusinessException.class, () -> useCase.listar(1L, null, null, false, 0, tamano));
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void paginaNegativaFalla() {
        assertThrows(BusinessException.class, () -> useCase.listar(1L, null, null, false, -1, 20));
        verifyNoInteractions(pedidoRepository);
    }
}
