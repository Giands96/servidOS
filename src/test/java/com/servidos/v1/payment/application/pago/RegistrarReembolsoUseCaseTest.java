package com.servidos.v1.payment.application.pago;

import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.payment.domain.event.PagoReembolsadoEvent;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaEntity;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import com.servidos.v1.payment.infrastructure.mapper.PagoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrarReembolsoUseCaseTest {

    @Mock
    PagoJpaRepository pagoRepository;
    @Mock
    EventPublisher eventPublisher;

    RegistrarReembolsoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegistrarReembolsoUseCase(pagoRepository, new PagoMapper(), eventPublisher);
        CurrentUser.setCurrentUser(1L);
        CurrentUser.setRole("ADMINISTRADOR");
    }

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private static PagoJpaEntity pago(EstadoPago estado) {
        PagoJpaEntity e = new PagoJpaEntity();
        e.setPagoId(5L);
        e.setPedidoId(1L);
        e.setRestauranteId(10L);
        e.setUsuarioId(2L);
        e.setMetodoPago(MetodoPago.EFECTIVO);
        e.setMonto(new BigDecimal("50.00"));
        e.setEstado(estado);
        return e;
    }

    private void dadoPago(PagoJpaEntity e) {
        when(pagoRepository.findByPagoIdAndRestauranteId(5L, 10L))
                .thenReturn(Optional.of(e));
    }

    @Test
    void reembolsaPagoPagadoConMotivo() {
        dadoPago(pago(EstadoPago.PAGADO));
        when(pagoRepository.save(any(PagoJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var reembolsado = useCase.ejecutar(new ReembolsarPagoCommand(5L, "Cobro duplicado"), 10L);

        assertEquals(EstadoPago.REEMBOLSADO, reembolsado.getEstado());
        var captor = ArgumentCaptor.forClass(PagoReembolsadoEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals(5L, captor.getValue().getPagoId());
        assertEquals(10L, captor.getValue().getRestauranteId());
    }

    @Test
    void soloAdministradorPuedeReembolsar() {
        CurrentUser.setRole("CAJERO");
        assertThrows(ForbiddenException.class,
                () -> useCase.ejecutar(new ReembolsarPagoCommand(5L, "Error"), 10L));
    }

    @Test
    void exigeMotivo() {
        dadoPago(pago(EstadoPago.PAGADO));
        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new ReembolsarPagoCommand(5L, "  "), 10L));
    }

    @Test
    void noReembolsaDosVeces() {
        dadoPago(pago(EstadoPago.REEMBOLSADO));
        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new ReembolsarPagoCommand(5L, "Otra vez"), 10L));
    }

    @Test
    void pagoDeOtroTenantNoSeEncuentra() {
        when(pagoRepository.findByPagoIdAndRestauranteId(5L, 99L))
                .thenReturn(Optional.empty());
        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new ReembolsarPagoCommand(5L, "Error"), 99L));
    }
}
