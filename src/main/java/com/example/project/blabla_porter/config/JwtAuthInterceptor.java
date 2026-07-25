package com.example.project.blabla_porter.config;

import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtAuthInterceptor.class);

    @Autowired
    private JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow CORS pre-flight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Missing or invalid Authorization header! Bearer token required.\"}");
            return false;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtService.validateTokenAndGetClaims(token);
            Long userId = Long.parseLong(claims.getSubject());
            User.UserRole role = User.UserRole.valueOf(claims.get("role", String.class));
            java.util.Set<User.UserRole> capabilities = jwtService.extractCapabilities(token);

            // Server-side context binding: never trust user id or role passed directly in request body or query params
            request.setAttribute("authenticatedUserId", userId);
            request.setAttribute("authenticatedUserRole", role);
            request.setAttribute("authenticatedUserCapabilities", capabilities);

            // Annotation-based role guard: read @RequireRole from the target controller method
            if (handler instanceof HandlerMethod) {
                HandlerMethod handlerMethod = (HandlerMethod) handler;
                RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);

                if (requireRole != null) {
                    User.UserRole[] allowedRoles = requireRole.value();
                    boolean hasAccess = false;
                    for (User.UserRole allowed : allowedRoles) {
                        if (capabilities.contains(allowed)) {
                            hasAccess = true;
                            break;
                        }
                    }
                    if (!hasAccess) {
                        String errMsg = String.format("Security Alert: User %d with capabilities %s was denied access to URI '%s'. Required roles: %s",
                                userId, capabilities, request.getRequestURI(), Arrays.toString(allowedRoles));
                        log.warn(errMsg);
                        io.sentry.Sentry.captureMessage(errMsg);
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"Forbidden: Access denied for capabilities "
                                + capabilities + ". Required: " + Arrays.toString(allowedRoles) + "\"}");
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid or expired JWT token: " + e.getMessage() + "\"}");
            return false;
        }
    }
}
