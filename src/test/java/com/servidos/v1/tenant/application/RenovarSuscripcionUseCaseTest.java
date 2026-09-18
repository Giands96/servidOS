package com.servidos.v1.tenant.application;

import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.application.suscripcion.RenovarSuscripcionCommand;
import com.servidos.v1.tenant.application.suscripcion.RenovarSuscripcionUseCase;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenovarSuscripcionUseCaseTest {

    @Mock SuscripcionJpaRepository suscripcionRepository;
    @Mock SuscripcionMapper suscripcionMapper;

    @InjectMocks RenovarSuscripcionUseCase useCase;

    private SuscripcionJpaEntity actual(LocalDate fin) {
        var e = new SuscripcionJpaEntity();
        e.setEstado(EstadoSuscripcion.ACTIVA);
        e.setFechaFin(fin);
        e.setPlanId(3L);
        return e;
    }

    @Test
    void vigenteExtiendeUnMesExacto() {
        var fin = LocalDate.now().plusDays(5);
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(actual(fin)));
        when(suscripcionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(suscripcionMapper.toEntity(any())).thenCallRealMethod();
        when(suscripcionMapper.toDomain(any())).thenCallRealMethod();

        var renovada = useCase.ejecutar(new RenovarSuscripcionCommand(7L));

        assertEquals(fin.plusDays(1), renovada.getFecha_inicio());
        assertEquals(fin.plusDays(1).plusMonths(1), renovada.getFecha_fin());
    }

    @Test
    void sinSuscripcionFalla() {
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new RenovarSuscripcionCommand(7L)));
    }

    @Test
    void inactivaFalla() {
        var e = actual(LocalDate.now().plusDays(5));
        e.setEstado(EstadoSuscripcion.CANCELADA);
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(e));
        assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new RenovarSuscripcionCommand(7L)));
    }
}
