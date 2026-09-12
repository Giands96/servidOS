package com.servidos.v1.shared.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitingFilter implements Filter {

    private static final int MAX_REQUESTS_PER_WINDOW = 5;
    private static final long WINDOW_MILLIS = 60_000;

    private record Ventana(int contador, long venceEn) {}

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String clientIp = req.getRemoteAddr();
        long ahora = System.currentTimeMillis();

        // Primero se pregunta si la ventana sigue viva, recién después se cuenta.
        // compute() es atómico por clave: sin carreras entre hilos.
        Ventana actual = ventanas.compute(clientIp, (ip, previa) -> {
            if (previa == null || ahora >= previa.venceEn()) {
                return new Ventana(1, ahora + WINDOW_MILLIS);
            }
            return new Ventana(previa.contador() + 1, previa.venceEn());
        });

        // Limpieza oportunista: evita que el mapa crezca para siempre.
        if (ventanas.size() > 1000) {
            ventanas.entrySet().removeIf(e -> ahora >= e.getValue().venceEn());
        }

        if (actual.contador() > MAX_REQUESTS_PER_WINDOW) {
            long esperaSeg = (actual.venceEn() - ahora + 999) / 1000;
            res.setStatus(429); // Too Many Requests (esta versión de Servlet no trae la constante)
            res.setHeader("Retry-After", String.valueOf(esperaSeg));
            res.getWriter().write("Demasiadas solicitudes. Por favor, inténtelo de nuevo más tarde.");
            return;
        }

        chain.doFilter(request, response);
    }

}
