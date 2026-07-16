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

        boolean skipExpiry = SKIP_EXPIRY_PATHS.stream().anyMatch(requestURI::contains);

        try {
            String username = null;
            try {
                // extractUsername calls the parser — throws if token is invalid or expired
                username = jwtUtil.extractUsername(token);
            } catch (io.jsonwebtoken.ExpiredJwtException expired) {
                if (skipExpiry) {
                    // [A07] Expiry not validated for these paths — an expired token is
                    //        accepted anyway. The parser rejects it, so we recover the
                    //        subject straight from the expired claims and authenticate as
                    //        that user. Someone who grabbed a token months ago still gets in.
                    username = expired.getClaims().getSubject();
                    com.mediconnect.ctf.CtfBehaviorRegistry.mark("a07-expiry-skip-path-bypass");
                }
                // non-skip path: expiry correctly rejected — leave unauthenticated.
            }

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (skipExpiry) {
                    setAuthentication(request, userDetails);
                } else {
                    if (jwtUtil.validateToken(token, userDetails)) {
                        setAuthentication(request, userDetails);
                    }
                }
            }
        } catch (io.jsonwebtoken.ExpiredJwtException ignoredExpiry) {
            // Expiry is a normal condition, not a fail-open — do not mark #34.
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException userMissing) {
            // Token parsed fine but the subject no longer exists (e.g. a deleted user, or
            // an expired token recovered on a skip-expiry path). That is not the malformed-
            // token fail-open case — do not mark #34 (avoids double-marking with #33).
        } catch (Exception e) {
            // [A07][#34] All other token parsing exceptions swallowed silently.
            //        A malformed or tampered token does not produce an error response —
            //        the request continues as unauthenticated, which combined with
            //        permitAll() routes means it still reaches the controller.
            com.mediconnect.ctf.CtfBehaviorRegistry.mark("a07-fail-open-token-validation");
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
