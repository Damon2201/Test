package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    // JWT secret injected from application.properties / environment variable
    @Value("${blabla.jwt.secret}")
    private String secretKeyString;

    // Token validity: 1 Hour (3,600,000 milliseconds)
    private static final long EXPIRATION_MS = 3600000L;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .claim("mobileNumber", user.getMobileNumber())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims validateTokenAndGetClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        Claims claims = validateTokenAndGetClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public User.UserRole extractRole(String token) {
        Claims claims = validateTokenAndGetClaims(token);
        return User.UserRole.valueOf(claims.get("role", String.class));
    }
}
