package com.mediconnect.ctf.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CTF-scoped error handling. The app-wide GlobalExceptionHandler deliberately
 * remaps every RuntimeException to HTTP 500 (an A04 verbose-error demo). That
 * would turn the CTF layer's 401/403/404 into 500s, so this advice - scoped to
 * the ctf controllers and handling the more specific ResponseStatusException -
 * preserves the intended status. Scoped, so it does not touch the app's
 * intentional behaviour elsewhere.
 */
@RestControllerAdvice(basePackages = "com.mediconnect.ctf")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CtfExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ex.getStatusCode().value());
        body.put("error", ex.getReason());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadInput(IllegalArgumentException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", ex.getMessage());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
