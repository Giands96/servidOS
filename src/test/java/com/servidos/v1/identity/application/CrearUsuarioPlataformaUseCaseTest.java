package com.servidos.v1.identity.application;

import com.servidos.v1.identity.application.usuario.CrearUsuarioPlataformaCommand;
import com.servidos.v1.identity.application.usuario.CrearUsuarioPlataformaUseCase;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.RolPlataformaJpaEntity;
import com.servidos.v1.identity.infrastructure.RolPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.mapper.UsuarioMapper;
import com.servidos.v1.shared.exception.ConflictException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrearUsuarioPlataformaUseCaseTest {

    @Mock UsuarioJpaRepository usuarioRepository;
    @Mock UsuarioPlataformaJpaRepository usuarioPlataformaRepository;
    @Mock RolPlataformaJpaRepository rolRepository;
    @Mock PasswordEncoder passwordEncoder;

    CrearUsuarioPlataformaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CrearUsuarioPlataformaUseCase(usuarioRepository, usuarioPlataformaRepository,
                rolRepository, new UsuarioMapper(), passwordEncoder);
        CurrentUser.setCurrentUser(1L);
        CurrentUser.setRole("SUPERADMIN");
    }

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private static CrearUsuarioPlataformaCommand cmd(Long rolId) {
        return new CrearUsuarioPlataformaCommand("Socia", "Pérez", "Socia@Mail.com", "secreto123", rolId);
    }

    private void dadoActorDePlataforma() {
        when(usuarioPlataformaRepository.findById(1L)).thenReturn(Optional.of(new UsuarioPlataformaJpaEntity()));
    }

    private void dadoRol(Long id, String nombre) {
        var r = new RolPlataformaJpaEntity();
        r.setRolPlataformaId(id);
        r.setNombre(nombre);
        r.setEstado("ACTIVO");
        when(rolRepository.findById(id)).thenReturn(Optional.of(r));
    }

    private void dadoAltaOk() {
        when(usuarioRepository.existsByEmail("socia@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("secreto123")).thenReturn("hash");
        when(usuarioRepository.saveAndFlush(any())).thenAnswer(i -> {
            var e = (com.servidos.v1.identity.infrastructure.UsuarioJpaEntity) i.getArgument(0);
            e.setUsuarioId(7L);
            return e;
        });
    }

    @ParameterizedTest
    @CsvSource({"SUPERADMIN, ADMIN", "SUPERADMIN, MODERADOR", "ADMIN, MODERADOR"})
    void creaRolesMenoresAlDelActor(String rolActor, String rolNuevo) {
        CurrentUser.setRole(rolActor);
        dadoActorDePlataforma();
        dadoRol(20L, rolNuevo);
        dadoAltaOk();

        var creado = useCase.ejecutar(cmd(20L));

        assertEquals(7L, creado.getUsuario_id());
        assertEquals("socia@mail.com", creado.getEmail());
        assertNull(creado.getPassword_hash());
        var membresia = ArgumentCaptor.forClass(UsuarioPlataformaJpaEntity.class);
        verify(usuarioPlataformaRepository).save(membresia.capture());
        assertEquals(7L, membresia.getValue().getUsuarioId());
        assertEquals(20L, membresia.getValue().getRolPlataformaId());
        assertEquals(EstadoUsuario.ACTIVO, membresia.getValue().getEstado());
    }

    /** La única diferencia entre SUPERADMIN y ADMIN: un ADMIN no crea otro ADMIN. Nadie crea SUPERADMIN. */
    @ParameterizedTest
    @CsvSource({"ADMIN, ADMIN", "ADMIN, SUPERADMIN", "SUPERADMIN, SUPERADMIN", "MODERADOR, MODERADOR"})
    void noCreaRolesIgualesOSuperiores(String rolActor, String rolNuevo) {
        CurrentUser.setRole(rolActor);
        dadoActorDePlataforma();
        dadoRol(20L, rolNuevo);

        assertThrows(ForbiddenException.class, () -> useCase.ejecutar(cmd(20L)));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void emailRepetidoEs409() {
        dadoActorDePlataforma();
        dadoRol(20L, "ADMIN");
        when(usuarioRepository.existsByEmail("socia@mail.com")).thenReturn(true);
        assertThrows(ConflictException.class, () -> useCase.ejecutar(cmd(20L)));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }
}
