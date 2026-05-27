package com.halilov.online.security;

import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security filter that authenticates each request from the
 * {@code Authorization: Bearer ...} header.
 *
 * <p>On every authenticated request we re-read the user row from the
 * DB to honor:
 * <ul>
 *   <li>Admin disable ({@code users.enabled = false}) — takes effect on
 *       the next request, not when the JWT eventually expires.</li>
 *   <li>Force-logout ({@code users.force_logout_at}) — admin password
 *       reset and self-service "log out everywhere" stamp this column;
 *       tokens issued at or before that instant fail freshness.</li>
 * </ul>
 *
 * <p>One extra row read per authenticated request — fine at current
 * traffic on a single-VM deployment, and the correctness win
 * (revocation latency = one request) is worth more than the cost.
 *
 * <p>Anything not authenticated (no header, invalid token, expired
 * token, missing user, disabled user) silently stays anonymous; the
 * security chain decides what's reachable to anonymous callers.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);

                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Re-check the user against DB so an admin disable / force-logout
                    // takes effect within one request, not when the JWT eventually
                    // expires. One extra row read per authenticated request — fine
                    // on the current low-traffic VM.
                    User u = users.findByEmail(email).orElse(null);
                    if (u != null && u.isEnabled() && tokenStillValid(claims, u)) {
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                        var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            } catch (Exception ignored) {
                // invalid token -> stay anonymous
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean tokenStillValid(Claims claims, User u) {
        if (u.getForceLogoutAt() == null) return true;
        java.util.Date iat = claims.getIssuedAt();
        if (iat == null) return false;
        return iat.toInstant().isAfter(u.getForceLogoutAt())
            || iat.toInstant().equals(u.getForceLogoutAt());
    }
}
