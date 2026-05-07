package com.mediconnect.controller;

import com.mediconnect.dto.LoginRequest;
import com.mediconnect.dto.RegisterRequest;
import com.mediconnect.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// [A07] No @Valid on request body — input is not even syntactically validated.
// [A07] No rate limiting on any endpoint.
// [A04] JWT returned in response body — accessible to JavaScript, vulnerable to XSS theft.
//        Correct approach: HttpOnly cookie with Secure and SameSite=Strict flags.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // [A07] Mass Assignment: role field from request body written directly into the User entity.
    // [A07] No password complexity validation.
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest request) {
        String token = authService.register(request);
        // [A04] JWT in JSON response body — not in an HttpOnly cookie
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("token", token));
    }

    // [A07] User Enumeration: raw error message from service forwarded to client without
    //        filtering — three distinct account states revealed to the attacker:
    //          "User not found: {username}"       → username does not exist
    //          "Invalid password"                 → username exists, wrong password
    //          "Account is locked until {time}"   → account exists, password correct, locked
    // [A07] No rate limiting — brute-force and credential stuffing attacks are possible.
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        try {
            String token = authService.login(request);
            // [A04] JWT in JSON response body — not in an HttpOnly cookie
            return ResponseEntity.ok(Map.of("token", token));
        } catch (RuntimeException e) {
            // [A07] Original error message forwarded without any generalization
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
