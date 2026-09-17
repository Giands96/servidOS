package com.servidos.v1.identity.infrastructure;

import com.servidos.v1.identity.domain.EstadoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRestauranteJpaRepository extends JpaRepository<UsuarioRestauranteJpaEntity, Long> {

    long countByRestauranteIdAndRolRestauranteIdAndEstado(
            Long restauranteId, Long rolRestauranteId, EstadoUsuario estado);
}
