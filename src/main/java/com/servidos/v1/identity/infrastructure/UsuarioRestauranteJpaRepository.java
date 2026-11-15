package com.servidos.v1.identity.infrastructure;

import com.servidos.v1.identity.application.usuario.UsuarioListItem;
import com.servidos.v1.identity.domain.EstadoUsuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRestauranteJpaRepository extends JpaRepository<UsuarioRestauranteJpaEntity, Long> {

    /**
     * Membresía del usuario en ESE restaurante. El tenant va en la query y no en un
     * if posterior: si el usuario es de otro restaurante, no aparece.
     */
    Optional<UsuarioRestauranteJpaEntity> findByUsuarioIdAndRestauranteId(Long usuarioId, Long restauranteId);

    @Query("""
            SELECT new com.servidos.v1.identity.application.usuario.UsuarioListItem(
                ur.usuarioId, u.nombre, u.apellido, u.email,
                ur.rolRestauranteId, r.nombre, ur.estado, u.ultimoAcceso)
            FROM UsuarioRestauranteJpaEntity ur
            JOIN UsuarioJpaEntity u ON u.usuarioId = ur.usuarioId
            JOIN RolRestauranteJpaEntity r ON r.rolRestauranteId = ur.rolRestauranteId
            WHERE ur.restauranteId = :restauranteId
            AND (:nombre IS NULL OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', CAST(:nombre AS string), '%')))
            AND (:apellido IS NULL OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', CAST(:apellido AS string), '%')))
            AND (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%')))
            AND (:rolRestauranteId IS NULL OR ur.rolRestauranteId = :rolRestauranteId)
            AND (:rolNombre IS NULL OR LOWER(r.nombre) LIKE LOWER(CONCAT('%', CAST(:rolNombre AS string), '%')))
            AND (:estado IS NULL OR ur.estado = :estado)
            """)
    Page<UsuarioListItem> buscarPorRestaurante(Long restauranteId, String nombre,
                                               String apellido,
                                               String email,
                                               Long rolRestauranteId,
                                               String rolNombre,
                                               EstadoUsuario estado,
                                               Pageable pageable);


    long countByRestauranteIdAndRolRestauranteIdAndEstado(
            Long restauranteId, Long rolRestauranteId, EstadoUsuario estado);
}
