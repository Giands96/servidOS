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
class ReactivarMesaUseCaseTest {

    @Mock
    MesaJpaRepository mesaRepository;

    ReactivarMesaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReactivarMesaUseCase(mesaRepository, new MesaMapper());
    }

    private static MesaJpaEntity entidad(boolean activa) {
        var e = new MesaJpaEntity();
        e.setMesaId(1L);
        e.setRestauranteId(10L);
        e.setNumero(5);
        e.setCapacidad(4);
        e.setEstado(EstadoMesa.LIBRE);
        e.setActiva(activa);
        return e;
    }

    private void dadaMesa(MesaJpaEntity e) {
        when(mesaRepository.bloquearPorIdYRestaurante(1L, 10L)).thenReturn(Optional.of(e));
    }

    @Test
    void reactivaLaMesaSiSuNumeroEstaLibre() {
        var e = entidad(false);
        dadaMesa(e);
        when(mesaRepository.saveAndFlush(any(MesaJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var mesa = useCase.ejecutar(1L, 10L);

        assertTrue(mesa.isActiva());
        assertTrue(e.isActiva());
    }

    @Test
    void numeroTomadoPorOtraMesaActivaDa400YNoGuarda() {
        var e = entidad(false);
        dadaMesa(e);
        when(mesaRepository.existsByRestauranteIdAndNumeroAndActivaTrueAndMesaIdNot(10L, 5, 1L)).thenReturn(true);

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, 10L));

        assertEquals("La mesa 5 ya existe para este restaurante", ex.getMessage());
        assertFalse(e.isActiva());
        verify(mesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicadoQueGanaLaCarreraEnLaBaseTambienDa400() {
        dadaMesa(entidad(false));
        when(mesaRepository.saveAndFlush(any(MesaJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq mesa"));

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, 10L));

        assertEquals("La mesa 5 ya existe para este restaurante", ex.getMessage());
    }

    @Test
    void mesaYaActivaDa400() {
        dadaMesa(entidad(true));

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, 10L));

        assertEquals("La mesa ya está activa", ex.getMessage());
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
