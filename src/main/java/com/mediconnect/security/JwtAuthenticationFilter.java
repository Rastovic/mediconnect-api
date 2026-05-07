package com.mediconnect.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    // [A07] Paths where token expiry validation is intentionally skipped.
    //        An attacker with an expired token can still authenticate against these routes.
    private static final List<String> SKIP_EXPIRY_PATHS = List.of(
            "/api/public/",
            "/api/legacy/",
            "/api/reports/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String requestURI = request.getRequestURI();

        try {
            // extractUsername calls the parser — throws if token is invalid or expired
            String username = jwtUtil.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                boolean skipExpiry = SKIP_EXPIRY_PATHS.stream().anyMatch(requestURI::contains);

                if (skipExpiry) {
                    // [A07] Expiry not validated for these paths — expired tokens are accepted.
                    //        An attacker who obtained a token months ago can still use it here.
                    setAuthentication(request, userDetails);
                } else {
                    if (jwtUtil.validateToken(token, userDetails)) {
                        setAuthentication(request, userDetails);
                    }
                }
            }
        } catch (Exception e) {
            // [A07] All token parsing exceptions swallowed silently.
            //        A malformed or tampered token does not produce an error response —
            //        the request continues as unauthenticated, which combined with
            //        permitAll() routes means it still reaches the controller.
        }

        chain.doFilter(request, response);
    }

    private void setAuthentication(HttpServletRequest request, UserDetails userDetails) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
