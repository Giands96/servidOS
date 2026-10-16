package com.servidos.v1.identity.application;

import com.servidos.v1.identity.application.usuario.RegistrarAdministradorRestauranteCommand;
import com.servidos.v1.identity.application.usuario.RegistrarAdministradorRestauranteUseCase;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.domain.event.UsuarioCreadoEvent;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaEntity;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.mapper.UsuarioMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrarAdministradorRestauranteUseCaseTest {

    private static final Long ROL_ADMIN_ID = 3L;
    private static final Long RESTAURANTE_ID = 5L;
    private static final Long USUARIO_ID = 77L;

    @Mock UsuarioJpaRepository usuarioRepository;
    @Mock UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    @Mock RolRestauranteJpaRepository rolRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EventPublisher eventPublisher;

    RegistrarAdministradorRestauranteUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegistrarAdministradorRestauranteUseCase(
                usuarioRepository, usuarioRestauranteRepository, rolRepository,
                new UsuarioMapper(), passwordEncoder, eventPublisher);
    }

    private static RegistrarAdministradorRestauranteCommand cmdBase() {
        return new RegistrarAdministradorRestauranteCommand("Ana", "Paz", " Ana@Demo.PE ", "secreta123");
    }

    private void stubRolAdministrador(String estado) {
        var rol = new RolRestauranteJpaEntity();
        rol.setRolRestauranteId(ROL_ADMIN_ID);
        rol.setNombre("ADMINISTRADOR");
        rol.setEstado(estado);
        when(rolRepository.findByNombre("ADMINISTRADOR")).thenReturn(Optional.of(rol));
    }

    private void stubGuardadoDeUsuario() {
        when(usuarioRepository.saveAndFlush(any(UsuarioJpaEntity.class))).thenAnswer(i -> {
            UsuarioJpaEntity e = i.getArgument(0);
            e.setUsuarioId(USUARIO_ID);
            return e;
        });
    }

    @Test
    void registraAdministradorConHashYMembresiaDelRolAdministrador() {
        stubRolAdministrador("ACTIVO");
        when(passwordEncoder.encode("secreta123")).thenReturn("HASH");
        stubGuardadoDeUsuario();

        var usuario = useCase.ejecutar(cmdBase(), RESTAURANTE_ID);

        var entityCaptor = ArgumentCaptor.forClass(UsuarioJpaEntity.class);
        verify(usuarioRepository).saveAndFlush(entityCaptor.capture());
        assertEquals("HASH", entityCaptor.getValue().getPasswordHash(), "se persiste el hash, nunca el password");
        assertEquals("ana@demo.pe", entityCaptor.getValue().getEmail());
        assertEquals(EstadoUsuario.ACTIVO, entityCaptor.getValue().getEstado());

        var membresiaCaptor = ArgumentCaptor.forClass(UsuarioRestauranteJpaEntity.class);
        verify(usuarioRestauranteRepository).save(membresiaCaptor.capture());
        assertEquals(USUARIO_ID, membresiaCaptor.getValue().getUsuarioId());
        assertEquals(RESTAURANTE_ID, membresiaCaptor.getValue().getRestauranteId());
        assertEquals(ROL_ADMIN_ID, membresiaCaptor.getValue().getRolRestauranteId());

        var eventCaptor = ArgumentCaptor.forClass(UsuarioCreadoEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertEquals(USUARIO_ID, eventCaptor.getValue().usuarioId());
        assertEquals(RESTAURANTE_ID, eventCaptor.getValue().restauranteId());
        assertEquals(ROL_ADMIN_ID, eventCaptor.getValue().rolId());
        assertEquals("ana@demo.pe", eventCaptor.getValue().email());

        assertEquals(USUARIO_ID, usuario.getUsuario_id());
        assertNull(usuario.getPassword_hash(), "el hash no sale del caso de uso");
    }

    @Test
    void emailDuplicadoEsConflictoYNoGuardaNada() {
        when(usuarioRepository.existsByEmail("ana@demo.pe")).thenReturn(true);

        var ex = assertThrows(ConflictException.class, () -> useCase.ejecutar(cmdBase(), RESTAURANTE_ID));

        assertEquals("El email ya existe", ex.getMessage());
        verify(usuarioRepository, never()).saveAndFlush(any());
        verifyNoInteractions(usuarioRestauranteRepository, eventPublisher);
    }

    @Test
    void carreraEnElUniqueDeEmailEsConflictoYNoCreaMembresia() {
        stubRolAdministrador("ACTIVO");
        when(passwordEncoder.encode("secreta123")).thenReturn("HASH");
        when(usuarioRepository.saveAndFlush(any(UsuarioJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_usuario_email"));

        var ex = assertThrows(ConflictException.class, () -> useCase.ejecutar(cmdBase(), RESTAURANTE_ID));

        assertEquals("El email ya existe", ex.getMessage());
        verifyNoInteractions(usuarioRestauranteRepository, eventPublisher);
    }

    @Test
    void rolAdministradorInexistenteFalla() {
        when(rolRepository.findByNombre("ADMINISTRADOR")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(cmdBase(), RESTAURANTE_ID));

        assertEquals("El rol ADMINISTRADOR no está disponible", ex.getMessage());
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void rolAdministradorInactivoFalla() {
        stubRolAdministrador("INACTIVO");

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(cmdBase(), RESTAURANTE_ID));

        assertEquals("El rol ADMINISTRADOR no está disponible", ex.getMessage());
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void passwordFueraDeRangoFalla() {
        var corto = new RegistrarAdministradorRestauranteCommand("Ana", "Paz", "ana@demo.pe", "1234567");
        var largo = new RegistrarAdministradorRestauranteCommand("Ana", "Paz", "ana@demo.pe", "x".repeat(73));

        assertThrows(BusinessException.class, () -> useCase.ejecutar(corto, RESTAURANTE_ID));
        assertThrows(BusinessException.class, () -> useCase.ejecutar(largo, RESTAURANTE_ID));
        verifyNoInteractions(passwordEncoder);
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void camposObligatoriosFaltantesFallan() {
        assertThrows(BusinessException.class, () -> useCase.ejecutar(null, RESTAURANTE_ID));
        assertThrows(BusinessException.class, () -> useCase.ejecutar(
                new RegistrarAdministradorRestauranteCommand(" ", "Paz", "ana@demo.pe", "secreta123"), RESTAURANTE_ID));
        assertThrows(BusinessException.class, () -> useCase.ejecutar(
                new RegistrarAdministradorRestauranteCommand("Ana", null, "ana@demo.pe", "secreta123"), RESTAURANTE_ID));
        assertThrows(BusinessException.class, () -> useCase.ejecutar(
                new RegistrarAdministradorRestauranteCommand("Ana", "Paz", "", "secreta123"), RESTAURANTE_ID));
        assertThrows(BusinessException.class, () -> useCase.ejecutar(
                new RegistrarAdministradorRestauranteCommand("Ana", "Paz", "ana@demo.pe", null), RESTAURANTE_ID));
        assertThrows(BusinessException.class, () -> useCase.ejecutar(cmdBase(), null));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void validarEmailDisponibleNormalizaYRechazaDuplicados() {
        when(usuarioRepository.existsByEmail("ana@demo.pe")).thenReturn(true);

        assertThrows(ConflictException.class, () -> useCase.validarEmailDisponible("  ANA@demo.pe "));
    }

    @Test
    void validarEmailDisponibleAceptaEmailLibre() {
        when(usuarioRepository.existsByEmail("libre@demo.pe")).thenReturn(false);

        useCase.validarEmailDisponible("Libre@Demo.pe");

        verify(usuarioRepository).existsByEmail("libre@demo.pe");
    }

    @Test
    void elComandoNoExponeElPasswordEnToString() {
        String texto = cmdBase().toString();

        assertFalse(texto.contains("secreta123"), "el password no puede salir en logs");
        assertTrue(texto.contains("Ana"));
    }

    @Test
    void exigeUnaTransaccionEnCurso() {
        var tx = RegistrarAdministradorRestauranteUseCase.class.getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, tx.propagation(),
                "debe correr dentro de la transacción de creación del restaurante");
    }
}
