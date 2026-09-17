package com.servidos.v1.identity.application;

import com.servidos.v1.identity.application.rol.AsignarRolPlataformaCommand;
import com.servidos.v1.identity.application.rol.AsignarRolPlataformaUseCase;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.RolPlataformaJpaEntity;
import com.servidos.v1.identity.infrastructure.RolPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.exception.UnauthorizedException;
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
class AsignarRolPlataformaTest {

    @Mock UsuarioPlataformaJpaRepository usuarioPlataformaRepository;
    @Mock RolPlataformaJpaRepository rolRepository;

    AsignarRolPlataformaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AsignarRolPlataformaUseCase(usuarioPlataformaRepository, rolRepository);
        CurrentUser.setCurrentUser(1L);
        CurrentUser.setRole("SuperAdmin");
    }

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private static UsuarioPlataformaJpaEntity membresia(Long usuarioId, Long rolId) {
        var m = new UsuarioPlataformaJpaEntity();
        m.setUsuarioId(usuarioId);
        m.setRolPlataformaId(rolId);
        m.setEstado(EstadoUsuario.ACTIVO);
        return m;
    }

    private static RolPlataformaJpaEntity rol(Long id, String nombre, String estado) {
        var r = new RolPlataformaJpaEntity();
        r.setRolPlataformaId(id);
        r.setNombre(nombre);
        r.setEstado(estado);
        return r;
    }

    @Test
    void sinSesionEs401() {
        CurrentUser.clear();

        assertThrows(UnauthorizedException.class, () ->
                useCase.ejecutar(new AsignarRolPlataformaCommand(2L, 20L)));
    }

    @Test
    void adminNoPuedeOtorgarAdmin() {
        CurrentUser.setRole("Admin");
        when(usuarioPlataformaRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 2L)));
        when(usuarioPlataformaRepository.findById(2L))
                .thenReturn(Optional.of(membresia(2L, 3L)));
        when(rolRepository.findById(20L))
                .thenReturn(Optional.of(rol(20L, "Admin", "ACTIVO")));

        assertThrows(ForbiddenException.class, () ->
                useCase.ejecutar(new AsignarRolPlataformaCommand(2L, 20L)));
    }

    @Test
    void rolInactivoSeRechaza() {
        when(usuarioPlataformaRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 1L)));
        when(usuarioPlataformaRepository.findById(2L))
                .thenReturn(Optional.of(membresia(2L, 3L)));
        when(rolRepository.findById(20L))
                .thenReturn(Optional.of(rol(20L, "Moderador", "INACTIVO")));

        assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new AsignarRolPlataformaCommand(2L, 20L)));
    }

    @Test
    void caminoFelizActualizaRol() {
        var objetivo = membresia(2L, 3L);
        when(usuarioPlataformaRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 1L)));
        when(usuarioPlataformaRepository.findById(2L)).thenReturn(Optional.of(objetivo));
        when(rolRepository.findById(20L))
                .thenReturn(Optional.of(rol(20L, "Moderador", "ACTIVO")));

        useCase.ejecutar(new AsignarRolPlataformaCommand(2L, 20L));

        assertEquals(20L, objetivo.getRolPlataformaId());
        verify(usuarioPlataformaRepository).save(objetivo);
    }
}
