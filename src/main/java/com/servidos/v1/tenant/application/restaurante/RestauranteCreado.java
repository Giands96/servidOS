package com.servidos.v1.tenant.application.restaurante;

import com.servidos.v1.identity.domain.Usuario;
import com.servidos.v1.tenant.domain.Restaurante;

/** Resultado del alta de plataforma: el restaurante y su dueño (ADMINISTRADOR), sin hash. */
public record RestauranteCreado(Restaurante restaurante, Usuario administrador) {}
