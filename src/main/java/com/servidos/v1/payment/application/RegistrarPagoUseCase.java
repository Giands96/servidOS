package com.servidos.v1.payment.application;

import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.payment.domain.Pago;
import com.servidos.v1.payment.domain.event.PagoRegistradoEvent;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaEntity;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import com.servidos.v1.payment.infrastructure.mapper.PagoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class RegistrarPagoUseCase {

    private final PagoJpaRepository pagoRepository;
    private final PedidoJpaRepository pedidoRepository;
    private final PagoMapper pagoMapper;
    private final EventPublisher eventPublisher;

    public record Command(Long pedidoId, MetodoPago metodoPago, BigDecimal montoEntregado, String referenciaExterna) {}

    @Transactional
    public Pago ejecutar(Command cmd, Long restauranteId, Long usuarioId) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (usuarioId == null) throw new BusinessException("Usuario no identificado");
        if (cmd.pedidoId() == null) throw new BusinessException("El pedido es obligatorio");
        if (cmd.metodoPago() == null) throw new BusinessException("El método de pago es obligatorio");

        var pedido = pedidoRepository.findByPedidoIdAndRestauranteId(cmd.pedidoId(), restauranteId)
                .orElseThrow(() -> new BusinessException("Pedido no encontrado"));

        if (pagoRepository.existsByPedidoIdAndRestauranteIdAndEstado(cmd.pedidoId(), restauranteId, EstadoPago.PAGADO)) {
            throw new BusinessException("Pedido ya pagado");
        }

        BigDecimal total = pedido.getTotal();
        BigDecimal vuelto = null;

        if (cmd.metodoPago() == MetodoPago.EFECTIVO) {
            if (cmd.montoEntregado() == null || cmd.montoEntregado().compareTo(total) < 0) {
                throw new BusinessException("Monto insuficiente");
            }
            vuelto = cmd.montoEntregado().subtract(total);
        }

        Pago pago = Pago.crear(cmd.pedidoId(), restauranteId, usuarioId, cmd.metodoPago(), total, vuelto, cmd.referenciaExterna());
        PagoJpaEntity saved = null;
        try{
            saved = pagoRepository.saveAndFlush(pagoMapper.toEntity(pago));

        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Pedido ya pagado");
        }
        eventPublisher.publish(new PagoRegistradoEvent(saved.getPagoId(), restauranteId, saved.getPedidoId()));
        return pagoMapper.toDomain(saved);
    }
}
