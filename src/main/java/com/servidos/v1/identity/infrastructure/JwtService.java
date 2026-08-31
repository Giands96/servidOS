package com.servidos.v1.identity.infrastructure;

import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public String generate(Long usuarioId, Long restauranteId, Long rolId) {
        return "stub-jwt";
    }
}
