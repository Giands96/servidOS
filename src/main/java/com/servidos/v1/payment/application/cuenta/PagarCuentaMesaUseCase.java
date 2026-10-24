package com.servidos.v1.payment.application.cuenta;

import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.payment.domain.Pago;
import com.servidos.v1.payment.domain.event.PagoRegistradoEvent;
import com.servidos.v1.payment.infrastructure.jpa.CuentaMesaJpaEntity;
import com.servidos.v1.payment.infrastructure.jpa.CuentaMesaJpaRepository;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cobra de una vez todo lo pendiente de una mesa, de forma atómica: o se crean todos los pagos o
 * ninguno.
 * <ol>
 *   <li>Bloquea la mesa (el mismo lock que usan los pedidos de mesa y la liberación), así una ronda
 *   nueva de mesa espera a que termine el cobro.</li>
 *   <li>Recalcula los pendientes bajo lock y exige que coincidan exactamente con los {@code pedidoIds}
 *   que vio el cajero: nunca se cobra algo que no vio. Un RECOJO/DELIVERY vinculado no toma el lock,
 *   pero entra en el recálculo.</li>
 *   <li>Crea un {@link Pago} por pedido (monto = total del pedido en la base) unidos por una fila de
 *   {@code cuenta_mesa}, que guarda el total, lo entregado y el vuelto (calculado una sola vez sobre
 *   el total). Un pago individual concurrente del mismo pedido choca con {@code UNIQUE(pedido_id)} y
 *   revierte toda la cuenta.</li>
 *   <li>Recién con todos los pagos guardados publica un {@link PagoRegistradoEvent} por pago, así la
 *   primera liberación automática de la mesa ya ve la cuenta entera cobrada.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class PagarCuentaMesaUseCase {

    private static final String CUENTA_CAMBIO = "La cuenta cambió, vuelva a cargarla";

    private final MesaJpaRepository mesaRepository;
    private final PedidoJpaRepository pedidoRepository;
    private final PagoJpaRepository pagoRepository;
    private final CuentaMesaJpaRepository cuentaRepository;
    private final PagoMapper pagoMapper;
    private final EventPublisher eventPublisher;

    @Transactional
    public CuentaPagada ejecutar(PagarCuentaMesaCommand cmd, Long restauranteId, Long usuarioId) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (usuarioId == null) throw new BusinessException("Usuario no identificado");
        if (cmd.mesaId() == null) throw new BusinessException("Mesa no identificada");
        if (cmd.metodoPago() == null) throw new BusinessException("El método de pago es obligatorio");
        if (cmd.pedidoIds() == null || cmd.pedidoIds().isEmpty()) {
            throw new BusinessException("Debe indicar los pedidos de la cuenta");
        }

        mesaRepository.bloquearPorIdYRestaurante(cmd.mesaId(), restauranteId)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada"));

        List<PedidoJpaEntity> pendientes = pedidoRepository.buscarPendientesDeCobroEnMesa(cmd.mesaId(), restauranteId);
        if (pendientes.isEmpty()) throw new BusinessException("La mesa no tiene pedidos pendientes de cobro");
        // Los ids repetidos no importan: el request se trata como un conjunto.
        Set<Long> pendientesIds = new HashSet<>();
        pendientes.forEach(p -> pendientesIds.add(p.getPedidoId()));
        if (!pendientesIds.equals(new HashSet<>(cmd.pedidoIds()))) throw new BusinessException(CUENTA_CAMBIO);

        BigDecimal total = pendientes.stream().map(PedidoJpaEntity::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vuelto = null;
        BigDecimal montoEntregado = null;
        if (cmd.metodoPago() == MetodoPago.EFECTIVO) {
            if (cmd.montoEntregado() == null || cmd.montoEntregado().compareTo(total) < 0) {
                throw new BusinessException("Monto insuficiente");
            }
            montoEntregado = cmd.montoEntregado();
            vuelto = montoEntregado.subtract(total);
        }

        var cuenta = new CuentaMesaJpaEntity();
        cuenta.setRestauranteId(restauranteId);
        cuenta.setMesaId(cmd.mesaId());
        cuenta.setUsuarioId(usuarioId);
        cuenta.setMetodoPago(cmd.metodoPago());
        cuenta.setTotal(total);
        cuenta.setMontoEntregado(montoEntregado);
        cuenta.setVuelto(vuelto);
        cuenta.setReferenciaExterna(cmd.referenciaExterna());
        Long cuentaId = cuentaRepository.saveAndFlush(cuenta).getCuentaId();

        List<PagoJpaEntity> pagos = new ArrayList<>();
        for (PedidoJpaEntity pedido : pendientes) {
            // El vuelto vive en la cuenta: cada pago lleva solo el monto de su pedido.
            Pago pago = Pago.crear(pedido.getPedidoId(), restauranteId, usuarioId, cmd.metodoPago(),
                    pedido.getTotal(), null, cmd.referenciaExterna());
            pago.setCuentaId(cuentaId);
            try {
                pagos.add(pagoRepository.saveAndFlush(pagoMapper.toEntity(pago)));
            } catch (DataIntegrityViolationException e) {
                // Un pago individual del mismo pedido se coló entre el recálculo y el insert.
                throw new BusinessException(CUENTA_CAMBIO);
            }
        }

        // Con todos los pagos guardados: la primera liberación de la mesa ve la cuenta entera cobrada.
        pagos.forEach(p -> eventPublisher.publish(
                new PagoRegistradoEvent(p.getPagoId(), restauranteId, p.getPedidoId())));

        return new CuentaPagada(cuentaId, cmd.mesaId(), total, cmd.metodoPago(), vuelto,
                pagos.stream().map(p -> new CuentaPagada.Pago(p.getPagoId(), p.getPedidoId(), p.getMonto())).toList());
    }
}
