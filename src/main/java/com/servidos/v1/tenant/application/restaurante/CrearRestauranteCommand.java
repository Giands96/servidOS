package com.servidos.v1.tenant.application.restaurante;

import com.servidos.v1.identity.application.usuario.RegistrarAdministradorRestauranteCommand;

public record CrearRestauranteCommand(String slug, String nombre, String direccion, Long planId, Boolean demo,
                                      Integer demoDias, RegistrarAdministradorRestauranteCommand administrador) {}
