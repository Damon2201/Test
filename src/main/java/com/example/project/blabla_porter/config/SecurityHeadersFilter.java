package com.example.project.blabla_porter.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Enforce HTTPS behind Render SSL terminator
        String xfp = req.getHeader("X-Forwarded-Proto");
        if (xfp != null && "http".equalsIgnoreCase(xfp)) {
            String secureUrl = "https://" + req.getServerName() + req.getRequestURI();
            if (req.getQueryString() != null) {
                secureUrl += "?" + req.getQueryString();
            }
            res.sendRedirect(secureUrl);
            return;
        }

        // Apply standard security headers
        res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline'; " +
                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://unpkg.com; " +
                "font-src https://fonts.gstatic.com; " +
                "img-src 'self' data: https://*.openstreetmap.org https://unpkg.com; " +
                "connect-src 'self' ws: wss: https://nominatim.openstreetmap.org;");

        chain.doFilter(request, response);
    }
}
