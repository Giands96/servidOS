package com.servidos.v1.tenant.application.restaurante;

public record CrearRestauranteCommand(String slug, String nombre, String direccion, Long planId, Boolean demo, Integer demoDias) {}
