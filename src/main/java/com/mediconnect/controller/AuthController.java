package com.mediconnect.controller;

import com.mediconnect.dto.LoginRequest;
import com.mediconnect.dto.RegisterRequest;
import com.mediconnect.entity.User;
import com.mediconnect.security.JwtUtil;
import com.mediconnect.security.UserPrincipal;
import com.mediconnect.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

// [A07] No @Valid on request body — input is not even syntactically validated.
// [A07] No rate limiting on any endpoint.
// [A06] JWT returned in response body — accessible to JavaScript, vulnerable to XSS theft.
//        Correct approach: HttpOnly cookie with Secure and SameSite=Strict flags.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    // [A07] Mass Assignment: role field from request body written directly into the User entity.
    // [A07] No password complexity validation.
    // [A07] User Enumeration: "Username already taken: {username}" reveals registered usernames.
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        try {
            User user = authService.register(request);
            String token = jwtUtil.generateToken(new UserPrincipal(user));
            // [A06] JWT in JSON response body — not in an HttpOnly cookie
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(buildAuthResponse(token, user));
        } catch (RuntimeException e) {
            // [A07] Raw error message forwarded — leaks which usernames exist
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // [A07] User Enumeration: raw error message from service forwarded to client without
    //        filtering — three distinct account states revealed to the attacker:
    //          "User not found: {email/username}"  → account does not exist
    //          "Invalid password"                  → account exists, wrong password
    //          "Account is locked until {time}"    → account exists, password correct, locked
    // [A07] No rate limiting — brute-force and credential stuffing attacks are possible.
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        try {
            User user = authService.login(request);
            String token = jwtUtil.generateToken(new UserPrincipal(user));
            // [A06] JWT in JSON response body — not in an HttpOnly cookie
            return ResponseEntity.ok(buildAuthResponse(token, user));
        } catch (RuntimeException e) {
            // [A07] Original error message forwarded without any generalization
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // [A06] passwordHash included in auth response — exposed to any JS reading the response
    private Map<String, Object> buildAuthResponse(String token, User user) {
        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("id", user.getId());
        userMap.put("email", user.getEmail());
        userMap.put("username", user.getUsername());
        userMap.put("firstName", user.getFirstName());
        userMap.put("lastName", user.getLastName());
        userMap.put("role", user.getRole().name());
        // [A06] passwordHash intentionally included — visible to client-side JavaScript
        userMap.put("passwordHash", user.getPasswordHash());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);
        response.put("user", userMap);
        return response;
    }
}
