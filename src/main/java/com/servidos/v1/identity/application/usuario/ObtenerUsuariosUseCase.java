package com.servidos.v1.identity.application.usuario;

import com.servidos.v1.identity.domain.RolNombreRestaurante;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista los usuarios de un restaurante.
 *
 * Errores corregidos en esta versión:
 * 1. El tenant venía dentro del Command: ahora llega solo por parámetro desde el controller.
 * 2. Se leía TenantContext acá para compararlo contra el mismo valor que el controller pasaba
 *    (5 == 5): se eliminó, no validaba nada y acoplaba application a la seguridad web.
 * 3. Faltaban los guards del actor (sesión, membresía y rol): los que un atacante sí puede disparar.
 * 4. Un tenant nulo lanzaba IllegalArgumentException, que cae en el handler genérico y devuelve 500.
 */
@Service
@RequiredArgsConstructor
public class ObtenerUsuariosUseCase {

    static final int TAMANO_MAXIMO = 50;

    private final UsuarioRestauranteJpaRepository usuarioRestauranteRepository;

    /**
     * @param pagina índice de página, desde 0
     * @param tamano filas por página, entre 1 y {@value #TAMANO_MAXIMO}
     */
    @Transactional(readOnly = true)
    public Page<UsuarioListItem> ejecutar(ObtenerUsuariosCommand cmd, Long restauranteId,
                                         int pagina, int tamano) {

        //* ERROR CORREGIDO: se validaba el tenant antes que el comando. El contrato de entrada va primero.
        if (cmd == null) {
            throw new BusinessException("El comando es obligatorio");
        }

        //* ERROR CORREGIDO: IllegalArgumentException no está mapeada (500). Sesión de plataforma
        //* es 400, con el mismo mensaje que usan los otros use cases del módulo.
        if (restauranteId == null) {
            throw new BusinessException("Operación no disponible para sesiones de plataforma");
        }

        Long actorId = CurrentUser.getCurrentUser();
        //* ERROR CORREGIDO: guard ausente. Sin sesión no hay actor que validar.
        if (actorId == null) {
            throw new UnauthorizedException("Sesión inválida");
        }

        // * ERROR CORREGIDO: no se comparaba el ThreadLocal contra sí mismo; se pregunta en DB
        // * si el actor pertenece a ESTE restaurante. Es la validación que sí puede fallar.
        if (usuarioRestauranteRepository.findByUsuarioIdAndRestauranteId(actorId, restauranteId).isEmpty()) {
            throw new ForbiddenException("Operación cross-tenant rechazada");
        }

        // ERROR CORREGIDO: guard de rol ausente. Además no es 400: falta de permiso es 403.
        // Se usa el enum del dominio para evitar el string mágico "ADMINISTRADOR".
        if (!RolNombreRestaurante.esAdministrador(CurrentUser.getRole())) {
            throw new ForbiddenException("Solo un ADMINISTRADOR puede listar usuarios");
        }

        // * Paginación validada acá: un `size` gigante es un DoS barato. Es 400, no 500.
        if (pagina < 0) {
            throw new BusinessException("La página no puede ser negativa");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new BusinessException("El tamaño de página debe estar entre 1 y " + TAMANO_MAXIMO);
        }

        return usuarioRestauranteRepository.buscarPorRestaurante(
                restauranteId,
                cmd.nombre(),
                cmd.apellido(),
                cmd.email(),
                cmd.rolRestauranteId(),
                cmd.rolNombre(),
                cmd.estado(),
                PageRequest.of(pagina, tamano));
    }
}
