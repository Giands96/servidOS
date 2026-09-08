package com.servidos.v1.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioPlataformaJpaRepository extends JpaRepository<UsuarioPlataformaJpaEntity, Long> {
}
