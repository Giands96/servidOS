package com.servidos.v1.identity.application.auth;

import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.RolPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaEntity;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.jpa.RefreshTokenJpaEntity;
import com.servidos.v1.identity.infrastructure.jpa.RefreshTokenJpaRepository;
import com.servidos.v1.identity.infrastructure.security.AuthProperties;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenJpaRepository repository;
    @Mock UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    @Mock UsuarioPlataformaJpaRepository usuarioPlataformaRepository;
    @Mock RolRestauranteJpaRepository rolRestauranteRepository;
    @Mock RolPlataformaJpaRepository rolPlataformaRepository;
    @Mock AuthProperties authProperties;

    @InjectMocks RefreshTokenService service;

    private void dadoRefreshDelRestaurante(Long restauranteId) {
        var token = new RefreshTokenJpaEntity();
        token.setId(1L);
        token.setUsuarioId(5L);
        token.setRestauranteId(restauranteId);
        token.setFamiliaId(UUID.randomUUID());
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(repository.findByHashToken(RefreshTokenService.sha256("crudo"))).thenReturn(Optional.of(token));
    }

    @Test
    void rotaSiLaMembresiaSigueEnElMismoRestaurante() {
        dadoRefreshDelRestaurante(100L);
        var membresia = new UsuarioRestauranteJpaEntity();
        membresia.setUsuarioId(5L);
        membresia.setRestauranteId(100L);
        membresia.setRolRestauranteId(10L);
        membresia.setEstado(EstadoUsuario.ACTIVO);
        when(usuarioRestauranteRepository.findByUsuarioIdAndRestauranteId(5L, 100L))
                .thenReturn(Optional.of(membresia));
        var rol = new RolRestauranteJpaEntity();
        rol.setNombre("RECEPCION");
        rol.setEstado("ACTIVO");
        when(rolRestauranteRepository.findById(10L)).thenReturn(Optional.of(rol));
        when(repository.save(any())).thenAnswer(i -> {
            RefreshTokenJpaEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(2L);
            return e;
        });

        var renovada = service.rotate("crudo");

        assertEquals(100L, renovada.restauranteId());
        assertEquals("RECEPCION", renovada.rol());
    }

    @Test
    void rechazaSiLaMembresiaYaNoEsDeEseRestaurante() {
        // El refresh dice restaurante 100, pero el usuario ya no tiene membresía ahí:
        // la query filtrada no la encuentra y no se emite un access para el tenant viejo.
        dadoRefreshDelRestaurante(100L);

        var ex = assertThrows(BusinessException.class, () -> service.rotate("crudo"));

        assertEquals("Refresh inválido", ex.getMessage());
        verify(repository, never()).save(any());
    }
}
