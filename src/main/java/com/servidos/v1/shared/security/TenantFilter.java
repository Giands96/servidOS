package com.servidos.v1.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servidos.v1.identity.infrastructure.security.JwtService;
import com.servidos.v1.shared.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            if(jwtService.isTokenValid(jwt) && SecurityContextHolder.getContext().getAuthentication() == null) {
                Long usuarioId = jwtService.extractUsuarioId(jwt);
                Long restauranteId = jwtService.extractRestauranteId(jwt);
                String rol = jwtService.extractRol(jwt);
                CurrentUser.setCurrentUser(usuarioId);
                CurrentUser.setRole(rol);
                TenantContext.setRestauranteId(restauranteId);
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(usuarioId,
                        null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + rol)));
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

        } catch (Exception e) {
            responderNoAutorizado(request, response);
            return;
        }

        try{
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            CurrentUser.clear();
        }


    }

    private void responderNoAutorizado(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value() + " " + HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message("Credenciales inválidas")
                .path(request.getRequestURI())
                .traceID(traceId)
                .build();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(cuerpo));
    }

}
