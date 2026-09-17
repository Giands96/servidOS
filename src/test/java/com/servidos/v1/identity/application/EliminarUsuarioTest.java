package com.servidos.v1.identity.application;

import com.servidos.v1.identity.application.usuario.EliminarUsuarioCommand;
import com.servidos.v1.identity.application.usuario.EliminarUsuarioUseCase;
import com.servidos.v1.identity.application.auth.RefreshTokenService;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaEntity;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ConflictException;
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
class EliminarUsuarioTest {

    @Mock UsuarioJpaRepository usuarioRepository;
    @Mock UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    @Mock RolRestauranteJpaRepository rolRepository;
    @Mock RefreshTokenService refreshTokenService;

    EliminarUsuarioUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new EliminarUsuarioUseCase(usuarioRepository, usuarioRestauranteRepository, rolRepository, refreshTokenService);
        CurrentUser.setCurrentUser(1L);
        CurrentUser.setRole("ADMINISTRADOR");
    }

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private static UsuarioRestauranteJpaEntity membresia(Long usuarioId, Long restauranteId) {
        return membresia(usuarioId, restauranteId, EstadoUsuario.ACTIVO);
    }

    private static UsuarioRestauranteJpaEntity membresia(Long usuarioId, Long restauranteId, EstadoUsuario estado) {
        var m = new UsuarioRestauranteJpaEntity();
        m.setUsuarioId(usuarioId);
        m.setRestauranteId(restauranteId);
        m.setRolRestauranteId(10L);
        m.setEstado(estado);
        return m;
    }

    private static UsuarioJpaEntity usuario(Long usuarioId, EstadoUsuario estado) {
        var u = new UsuarioJpaEntity();
        u.setUsuarioId(usuarioId);
        u.setEmail("cocina@demo.pe");
        u.setEstado(estado);
        return u;
    }

    private static RolRestauranteJpaEntity rol(Long id, String nombre) {
        var r = new RolRestauranteJpaEntity();
        r.setRolRestauranteId(id);
        r.setNombre(nombre);
        r.setEstado("ACTIVO");
        return r;
    }

    @Test
    void comandoNuloEs400() {
        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(null, 100L));
        assertEquals("El comando es obligatorio", ex.getMessage());
    }

    @Test
    void usuarioNuloEs400() {
        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new EliminarUsuarioCommand(null), 100L));
        assertEquals("El usuario es obligatorio", ex.getMessage());
    }

    @Test
    void sesionDePlataformaEs400() {
        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new EliminarUsuarioCommand(2L), null));
        assertEquals("Operación no disponible para sesiones de plataforma", ex.getMessage());
    }

    @Test
    void sinSesionEs401() {
        CurrentUser.clear();

        assertThrows(UnauthorizedException.class,
                () -> useCase.ejecutar(new EliminarUsuarioCommand(2L), 100L));
    }

    @Test
    void autoEliminacionEs403() {
        var ex = assertThrows(ForbiddenException.class,
                () -> useCase.ejecutar(new EliminarUsuarioCommand(1L), 100L));
        assertEquals("No puedes eliminar tu propio usuario", ex.getMessage());
        verifyNoInteractions(usuarioRestauranteRepository, usuarioRepository, refreshTokenService);
    }

    @Test
    void actorDeOtroTenantEs403() {
        when(usuarioRestauranteRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 100L)));

        var ex = assertThrows(ForbiddenException.class, () ->
                useCase.ejecutar(new EliminarUsuarioCommand(2L), 999L));
        assertEquals("Operación cross-tenant rechazada", ex.getMessage());
    }

    @Test
    void noAdministradorEs403() {
        CurrentUser.setRole("CAJERO");
        when(usuarioRestauranteRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 100L)));

        var ex = assertThrows(ForbiddenException.class, () ->
                useCase.ejecutar(new EliminarUsuarioCommand(2L), 100L));
        assertEquals("Solo un ADMINISTRADOR puede eliminar usuarios", ex.getMessage());
    }

    @Test
    void objetivoInexistenteEs400() {
        when(usuarioRestauranteRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 100L)));
        when(usuarioRestauranteRepository.findById(2L)).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new EliminarUsuarioCommand(2L), 100L));
        assertEquals("El usuario no pertenece a este restaurante", ex.getMessage());
    }

    @Test
    void objetivoDeOtroTenantEs403() {
        when(usuarioRestauranteRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 100L)));
        when(usuarioRestauranteRepository.findById(2L))
                .thenReturn(Optional.of(membresia(2L, 777L)));

        assertThrows(ForbiddenException.class, () ->
                useCase.ejecutar(new EliminarUsuarioCommand(2L), 100L));
    }

    @Test
    void yaEliminadoEs400() {
        when(usuarioRestauranteRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 100L)));
        when(usuarioRestauranteRepository.findById(2L))
                .thenReturn(Optional.of(membresia(2L, 100L, EstadoUsuario.DESHABILITADO)));

        var ex = assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new EliminarUsuarioCommand(2L), 100L));
        assertEquals("El usuario ya fue eliminado", ex.getMessage());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void caminoFelizDeshabilitaYRevocaSesiones() {
        var objetivoMembresia = membresia(2L, 100L, EstadoUsuario.ACTIVO);
        var objetivoUsuario = usuario(2L, EstadoUsuario.ACTIVO);
        when(usuarioRestauranteRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 100L, EstadoUsuario.ACTIVO)));
        when(usuarioRestauranteRepository.findById(2L)).thenReturn(Optional.of(objetivoMembresia));
        when(rolRepository.findById(10L)).thenReturn(Optional.of(rol(10L, "RECEPCION")));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(objetivoUsuario));

        useCase.ejecutar(new EliminarUsuarioCommand(2L), 100L);

        assertEquals(EstadoUsuario.DESHABILITADO, objetivoMembresia.getEstado());
        assertEquals(EstadoUsuario.DESHABILITADO, objetivoUsuario.getEstado());
        verify(usuarioRestauranteRepository).save(objetivoMembresia);
        verify(usuarioRepository).save(objetivoUsuario);
        verify(refreshTokenService).revokeAll(2L);
    }

    @Test
    void eliminarUltimoAdministradorEs409() {
        var objetivoMembresia = membresia(2L, 100L, EstadoUsuario.ACTIVO);
        when(usuarioRestauranteRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 100L, EstadoUsuario.ACTIVO)));
        when(usuarioRestauranteRepository.findById(2L)).thenReturn(Optional.of(objetivoMembresia));
        when(rolRepository.findById(10L)).thenReturn(Optional.of(rol(10L, "ADMINISTRADOR")));
        when(usuarioRestauranteRepository.countByRestauranteIdAndRolRestauranteIdAndEstado(
                100L, 10L, EstadoUsuario.ACTIVO)).thenReturn(1L);

        var ex = assertThrows(ConflictException.class, () ->
                useCase.ejecutar(new EliminarUsuarioCommand(2L), 100L));
        assertEquals("No puedes eliminar al último ADMINISTRADOR del restaurante", ex.getMessage());
        verify(usuarioRestauranteRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void eliminarAdministradorConParFunciona() {
        var objetivoMembresia = membresia(2L, 100L, EstadoUsuario.ACTIVO);
        var objetivoUsuario = usuario(2L, EstadoUsuario.ACTIVO);
        when(usuarioRestauranteRepository.findById(1L))
                .thenReturn(Optional.of(membresia(1L, 100L, EstadoUsuario.ACTIVO)));
        when(usuarioRestauranteRepository.findById(2L)).thenReturn(Optional.of(objetivoMembresia));
        when(rolRepository.findById(10L)).thenReturn(Optional.of(rol(10L, "ADMINISTRADOR")));
        when(usuarioRestauranteRepository.countByRestauranteIdAndRolRestauranteIdAndEstado(
                100L, 10L, EstadoUsuario.ACTIVO)).thenReturn(2L);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(objetivoUsuario));

        useCase.ejecutar(new EliminarUsuarioCommand(2L), 100L);

        assertEquals(EstadoUsuario.DESHABILITADO, objetivoMembresia.getEstado());
        verify(usuarioRestauranteRepository).save(objetivoMembresia);
        verify(refreshTokenService).revokeAll(2L);
    }
}
