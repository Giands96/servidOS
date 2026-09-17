package com.servidos.v1.shared.exception;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/usuarios");
    }

    @AfterEach
    void limpiar() {
        assertNotNull(request);
    }

    @Test
    void forbiddenDevuelve403ConTraceId() {
        var resp = handler.handleForbidden(new ForbiddenException("Operación cross-tenant rechazada"), request);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertNotNull(resp.getBody().getTraceID(), "P1.9 exige traceId");
        assertFalse(resp.getBody().getTraceID().isBlank());
        assertTrue(resp.getBody().getStatus().startsWith("403"));
    }

    @Test
    void accessDeniedDeSpringTambienEs403() {
        var resp = handler.handleForbidden(new AccessDeniedException("denegado"), request);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertNotNull(resp.getBody().getTraceID());
    }

    @Test
    void validacionDevuelve422ConTraceId() {
        var binding = new BeanPropertyBindingResult(new Object(), "cmd");
        binding.addError(new FieldError("cmd", "email", "no debe estar vacío"));
        Method metodo = GlobalExceptionHandlerTest.class.getDeclaredMethods()[0];
        var ex = new MethodArgumentNotValidException(new MethodParameter(metodo, -1), binding);
        var resp = handler.handleValidation(ex, request);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.getStatusCode());
        assertNotNull(resp.getBody().getTraceID());
        assertTrue(resp.getBody().getMessage().contains("email"));
    }

    @Test
    void businessSigueEn400YConTraceId() {
        var resp = handler.handleBusinessException(new BusinessException("Credenciales inválidas"), request);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertNotNull(resp.getBody().getTraceID());
    }

    @Test
    void errorInesperadoNoFiltraDetalle() {
        var resp = handler.handleGeneralException(new RuntimeException("SELECT * FROM usuario"), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals("An unexpected error occurred.", resp.getBody().getMessage());
        assertNotNull(resp.getBody().getTraceID());
    }
}
