package com.example.project.blabla_porter.config;

import com.example.project.blabla_porter.model.User;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which roles are allowed to invoke a controller method.
 * The JwtAuthInterceptor reads this annotation and returns 403
 * if the authenticated user's role is not in the allowed list.
 *
 * Methods WITHOUT this annotation are accessible to any authenticated user.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    User.UserRole[] value();
}
