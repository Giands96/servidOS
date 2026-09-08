package com.servidos.v1.identity.infrastructure.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final AuthProperties authProperties;

    // Spring inyecta AuthProperties automáticamente por el constructor
    public JwtService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }


    private SecretKey getSigningKey() {
        byte[] keyBytes = authProperties.secret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generate(Long usuarioId, Long restauranteId, Long rolId) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + authProperties.accessTtl().toMillis());

        return Jwts.builder()
                .subject(usuarioId.toString())
                .id(UUID.randomUUID().toString())
                .claim("restauranteId", restauranteId)
                .claim("rolId", rolId)
                .audience().add(authProperties.audience()).and()
                .issuer(authProperties.issuer())
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(authProperties.issuer())
                .requireAudience(authProperties.audience())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractRestauranteId(String token) {
        return extractAllClaims(token).get("restauranteId", Long.class);
    }

    public Long extractUsuarioId(String token) {
        return Long.parseLong(extractAllClaims(token).getSubject());
    }

    public Long extractRolId(String token) {
        return extractAllClaims(token).get("rolId", Long.class);
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

}