package com.servidos.v1.payment.application.pago;

import com.servidos.v1.payment.domain.Pago;
import com.servidos.v1.payment.domain.event.PagoReembolsadoEvent;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaEntity;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import com.servidos.v1.payment.infrastructure.mapper.PagoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrarReembolsoUseCase {

    private final PagoJpaRepository pagoRepository;
    private final PagoMapper pagoMapper;
    private final EventPublisher eventPublisher;

    @Transactional
    public Pago ejecutar(ReembolsarPagoCommand cmd, Long restauranteId) {
        if (cmd.pagoId() == null) throw new BusinessException("El pago es obligatorio");
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        Long actorId = CurrentUser.getCurrentUser();
        if (actorId == null) throw new BusinessException("Usuario no identificado");
        if (!"ADMINISTRADOR".equalsIgnoreCase(CurrentUser.getRole())) {
            throw new ForbiddenException("Solo un ADMINISTRADOR puede reembolsar pagos");
        }

        PagoJpaEntity entity = pagoRepository.findByPagoIdAndRestauranteId(cmd.pagoId(), restauranteId)
                .orElseThrow(() -> new BusinessException("Pago no encontrado"));
        Pago pago = pagoMapper.toDomain(entity);
        pago.reembolsar(cmd.motivo(), actorId);
        PagoJpaEntity guardado = pagoRepository.save(pagoMapper.toEntity(pago));

        eventPublisher.publish(new PagoReembolsadoEvent(guardado.getPagoId(), restauranteId, guardado.getPedidoId()));
        return pagoMapper.toDomain(guardado);
    }
}
