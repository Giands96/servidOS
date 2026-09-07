package com.servidos.v1.identity.infrastructure;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

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
                .claim("restauranteId", restauranteId)
                .claim("rolId", rolId)
                .claim("aud", authProperties.audience())
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
        } catch (Exception e) {
            return false;
        }
    }

}