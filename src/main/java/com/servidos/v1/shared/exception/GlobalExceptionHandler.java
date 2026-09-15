package com.servidos.v1.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    //* Logger para registrar los errores en el log del servidor
    private static final Logger logger = Logger.getLogger(GlobalExceptionHandler.class.getName());

    private static String nuevoTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static ErrorResponse cuerpo(HttpStatus status, String message, String path, String traceId) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value() + " " + status.getReasonPhrase())
                .message(message)
                .path(path)
                .traceID(traceId)
                .build();
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        String traceId = nuevoTraceId();
        logger.severe("[" + traceId + "] Business: " + ex.getMessage());
        return new ResponseEntity<>(
                cuerpo(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), traceId),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        String traceId = nuevoTraceId();
        logger.severe("[" + traceId + "] Unauthorized: " + ex.getMessage());
        return new ResponseEntity<>(
                cuerpo(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI(), traceId),
                HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(RuntimeException ex, HttpServletRequest request) {
        String traceId = nuevoTraceId();
        logger.severe("[" + traceId + "] Forbidden: " + ex.getMessage());
        return new ResponseEntity<>(
                cuerpo(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI(), traceId),
                HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        String traceId = nuevoTraceId();
        logger.severe("[" + traceId + "] Conflict: " + ex.getMessage());
        return new ResponseEntity<>(
                cuerpo(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI(), traceId),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String traceId = nuevoTraceId();
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Datos inválidos";
        }
        logger.severe("[" + traceId + "] Validation: " + message);
        return new ResponseEntity<>(
                cuerpo(HttpStatus.UNPROCESSABLE_ENTITY, message, request.getRequestURI(), traceId),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        String traceId = nuevoTraceId();
        logger.severe("[" + traceId + "] Unexpected: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        return new ResponseEntity<>(
                cuerpo(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request.getRequestURI(), traceId),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
