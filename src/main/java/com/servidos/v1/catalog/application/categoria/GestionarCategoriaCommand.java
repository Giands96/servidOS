package com.servidos.v1.catalog.application.categoria;

import com.servidos.v1.catalog.domain.EstadoCategoria;

public record GestionarCategoriaCommand(
        String nombre,
        EstadoCategoria estado
) {}
