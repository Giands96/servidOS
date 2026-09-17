package com.servidos.v1.identity.application;

import com.servidos.v1.identity.application.rol.AsignarRolRestauranteCommand;
import com.servidos.v1.identity.application.rol.AsignarRolRestauranteUseCase;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaEntity;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsignarRolRestauranteTest {

    @Mock UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    @Mock RolRestauranteJpaRepository rolRepository;

    AsignarRolRestauranteUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AsignarRolRestauranteUseCase(usuarioRestauranteRepository, rolRepository);
        CurrentUser.setCurrentUser(1L);
        CurrentUser.setRole("ADMINISTRADOR");
    }

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private static UsuarioRestauranteJpaEntity membresia(Long usuarioId, Long restauranteId) {
        var m = new UsuarioRestauranteJpaEntity();
        m.setUsuarioId(usuarioId);
        m.setRestauranteId(restauranteId);
        return m;
    }

    private static RolRestauranteJpaEntity rol(Long id, String nombre, String estado) {
        var r = new RolRestauranteJpaEntity();
        r.setRolRestauranteId(id);
        r.setNombre(nombre);
        r.setEstado(estado);
        return r;
    }

    @Test
    void crossTenantEs403() {
        when(usuarioRestauranteRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 100L)));

        var ex = assertThrows(ForbiddenException.class, () ->
                useCase.ejecutar(new AsignarRolRestauranteCommand(2L, 20L), 999L));
        assertEquals("Operación cross-tenant rechazada", ex.getMessage());
    }

    @Test
    void administradorNoPuedeOtorgarAdministrador() {
        when(usuarioRestauranteRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 100L)));
        when(usuarioRestauranteRepository.findById(2L))
                .thenReturn(Optional.of(membresia(2L, 100L)));
        when(rolRepository.findById(20L))
                .thenReturn(Optional.of(rol(20L, "ADMINISTRADOR", "ACTIVO")));

        assertThrows(ForbiddenException.class, () ->
                useCase.ejecutar(new AsignarRolRestauranteCommand(2L, 20L), 100L));
    }

    @Test
    void rolInactivoSeRechaza() {
        when(usuarioRestauranteRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 100L)));
        when(usuarioRestauranteRepository.findById(2L))
                .thenReturn(Optional.of(membresia(2L, 100L)));
        when(rolRepository.findById(20L))
                .thenReturn(Optional.of(rol(20L, "RECEPCION", "INACTIVO")));

        assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new AsignarRolRestauranteCommand(2L, 20L), 100L));
    }

    @Test
    void caminoFelizActualizaRol() {
        var objetivo = membresia(2L, 100L);
        when(usuarioRestauranteRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 100L)));
        when(usuarioRestauranteRepository.findById(2L)).thenReturn(Optional.of(objetivo));
        when(rolRepository.findById(20L))
                .thenReturn(Optional.of(rol(20L, "RECEPCION", "ACTIVO")));

        useCase.ejecutar(new AsignarRolRestauranteCommand(2L, 20L), 100L);

        assertEquals(20L, objetivo.getRolRestauranteId());
        verify(usuarioRestauranteRepository).save(objetivo);
    }
}
