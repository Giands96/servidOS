package com.servidos.v1.identity.infrastructure;

import com.servidos.v1.identity.domain.EstadoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRestauranteJpaRepository extends JpaRepository<UsuarioRestauranteJpaEntity, Long> {

    /**
     * Membresía del usuario en ESE restaurante. El tenant va en la query y no en un
     * if posterior: si el usuario es de otro restaurante, no aparece.
     */
    Optional<UsuarioRestauranteJpaEntity> findByUsuarioIdAndRestauranteId(Long usuarioId, Long restauranteId);

    long countByRestauranteIdAndRolRestauranteIdAndEstado(
            Long restauranteId, Long rolRestauranteId, EstadoUsuario estado);
}
