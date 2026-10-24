package com.servidos.v1.payment.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CuentaMesaJpaRepository extends JpaRepository<CuentaMesaJpaEntity, Long> {
}
