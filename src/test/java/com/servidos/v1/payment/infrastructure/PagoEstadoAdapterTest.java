package com.servidos.v1.payment.infrastructure;

import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoEstadoAdapterTest {

    @Mock
    PagoJpaRepository pagoRepository;

    @Test
    void pagadoCuandoExistePagoConEstadoPagado() {
        var adapter = new PagoEstadoAdapter(pagoRepository);
        when(pagoRepository.existsByPedidoIdAndRestauranteIdAndEstado(5L, 10L, EstadoPago.PAGADO))
                .thenReturn(true);

        assertTrue(adapter.estaPagado(5L, 10L));
        verify(pagoRepository).existsByPedidoIdAndRestauranteIdAndEstado(5L, 10L, EstadoPago.PAGADO);
    }

    @Test
    void noPagadoCuandoNoHayPagoConEstadoPagado() {
        var adapter = new PagoEstadoAdapter(pagoRepository);
        when(pagoRepository.existsByPedidoIdAndRestauranteIdAndEstado(5L, 10L, EstadoPago.PAGADO))
                .thenReturn(false);

        assertFalse(adapter.estaPagado(5L, 10L));
    }
}
