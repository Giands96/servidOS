package com.servidos.v1.identity.application;

import com.servidos.v1.identity.domain.Usuario;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrearEmpleadoUseCase {

    private final CrearUsuarioUseCase crearUsuarioUseCase;

    /*
    *   Un Empleado debería de contar con los datos del Usuario,
    *   el rol que tiene dentro del restaurante,
    *   y el restaurante al que pertenece.
    *
    *   Wrapper fino sobre CrearUsuarioUseCase para no duplicar reglas P1.7
    *   (actor, tenant, jerarquía, rol ACTIVO, 409, sin hash).
    * */

    public record Command(
            String nombre,
            String apellido,
            String email,
            String password,
            Long rolRestauranteId,
            Long restauranteId
            ){}

    @Transactional
    public Usuario ejecutar(Command command) {
        validar(command, command == null ? null : command.restauranteId());
        return crearUsuarioUseCase.ejecutar(
                new CrearUsuarioUseCase.Command(
                        command.nombre(),
                        command.apellido(),
                        command.email(),
                        command.password(),
                        command.rolRestauranteId()),
                command.restauranteId());
    }

    /** Sobrecarga histórica: se ignora el segundo parámetro y manda el del Command. */
    @Transactional
    public Usuario ejecutar(Command command, Long restauranteIdIgnorado) {
        return ejecutar(command);
    }

    private void validar(Command cmd, Long restauranteId) {
        if (cmd == null) {
            throw new BusinessException("El comando es obligatorio");
        }
        if (cmd.nombre() == null || cmd.nombre().trim().isEmpty()) {
            throw new BusinessException("El nombre es obligatorio");
        }

        // Validar que el apellido no sea nulo o vacío
        if(cmd.apellido() == null || cmd.apellido().trim().isEmpty()) {
            throw new BusinessException("El apellido es obligatorio");
        }

        if (cmd.email() == null || cmd.email().trim().isEmpty()) {
            throw new BusinessException("El email es obligatorio");
        }

        if (cmd.password() == null || cmd.password().isEmpty()) {
            throw new BusinessException("El password es obligatorio");
        }

        if (cmd.rolRestauranteId() == null) {
            throw new BusinessException("El rol es obligatorio");
        }

        if (restauranteId == null) {
            throw new BusinessException("El restaurante id es obligatorio");
        }
    }


}
